package com.minhaempresa.recrutamentoapi.settings.service;

import com.minhaempresa.recrutamentoapi.settings.dto.request.CompanySettingsUpdateRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.response.CompanySettingsResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PublicSettingsResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.CompanySettings;
import com.minhaempresa.recrutamentoapi.settings.entity.ConsentTextVersion;
import com.minhaempresa.recrutamentoapi.settings.repository.CompanySettingsRepository;
import com.minhaempresa.recrutamentoapi.settings.repository.ConsentTextVersionRepository;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.storage.FileType;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageService;
import com.minhaempresa.recrutamentoapi.shared.storage.StoredFile;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Company settings (single row): contacts, logo, data retention and the versioned consent texts
 * (Lei n.º 22/11). Changing a consent text creates a new version and keeps the exact texts of it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    /** Same limit as the frontend. */
    public static final int MAX_LOGO_BYTES = 300 * 1024;
    private static final String LOGO_CATEGORY = "company-logo";
    private static final String LOGO_PATH = "/api/v1/public/settings/logo";
    private static final int LOGO_HASH_LENGTH = 12;

    private final CompanySettingsRepository repository;
    private final ConsentTextVersionRepository consentVersions;
    private final StorageService storage;
    private final Clock clock;

    /** A stored logo ready to be served. */
    public record LogoFile(Resource content, String contentType, String hash) {}

    @Cacheable(cacheNames = CacheNames.COMPANY_SETTINGS, key = "'admin'")
    @Transactional(readOnly = true)
    public CompanySettingsResponse get() {
        return toResponse(load());
    }

    @Cacheable(cacheNames = CacheNames.COMPANY_SETTINGS, key = "'public'")
    @Transactional(readOnly = true)
    public PublicSettingsResponse getPublic() {
        CompanySettings s = load();
        return new PublicSettingsResponse(s.getName(), s.getLegalName(), s.getEmail(), s.getPhone(), s.getAddress(), s.getWebsite(),
                logoUrl(s), s.getDataRetentionMonths(), s.getConsentTextVersion(), s.getConsentJobText(), s.getConsentTalentPoolText());
    }

    /** Partial update; a changed consent text gets a new version ({@code 2026.1} → {@code 2026.2}). */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.COMPANY_SETTINGS, allEntries = true)
    public CompanySettingsResponse update(CompanySettingsUpdateRequest request) {
        CompanySettings s = load();
        setIfPresent(request.name(), v -> s.setName(v.trim()));
        setIfPresent(request.legalName(), v -> s.setLegalName(v.trim()));
        setIfPresent(request.nif(), s::setNif);
        setIfPresent(request.email(), v -> s.setEmail(v.trim().toLowerCase(java.util.Locale.ROOT)));
        setIfPresent(request.phone(), v -> s.setPhone(PhoneNumbers.normalizeOrThrow(v)));
        setIfPresent(request.address(), v -> s.setAddress(v.trim()));
        setIfPresent(request.website(), v -> s.setWebsite(v.trim()));
        if (request.dataRetentionMonths() != null) {
            s.setDataRetentionMonths(request.dataRetentionMonths());
        }
        String jobText = request.consentJobText() == null ? s.getConsentJobText() : request.consentJobText().trim();
        String poolText = request.consentTalentPoolText() == null ? s.getConsentTalentPoolText() : request.consentTalentPoolText().trim();
        if (!jobText.equals(s.getConsentJobText()) || !poolText.equals(s.getConsentTalentPoolText())) {
            String version = nextVersion(s.getConsentTextVersion(), clock.instant().atZone(ZoneOffset.UTC).getYear());
            consentVersions.save(new ConsentTextVersion(version, jobText, poolText));
            s.setConsentTextVersion(version);
            s.setConsentJobText(jobText);
            s.setConsentTalentPoolText(poolText);
        }
        repository.flush();
        return toResponse(s);
    }

    /**
     * Replaces the logo. PNG, JPEG or WebP (detected from the content) up to 300 KB; the storage key
     * is generated, the original name is only kept as metadata.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.COMPANY_SETTINGS, allEntries = true)
    public CompanySettingsResponse uploadLogo(String originalName, byte[] content) {
        if (content.length == 0 || content.length > MAX_LOGO_BYTES) {
            throw invalidLogo("settings.logo.too-large");
        }
        FileType type = FileType.detect(content).orElseThrow(() -> invalidLogo("settings.logo.invalid-type"));
        CompanySettings s = load();
        StoredFile stored = storage.store(LOGO_CATEGORY, type, content);
        String previousKey = s.getLogoKey();
        s.setLogoKey(stored.key());
        s.setLogoContentType(type.mimeType());
        s.setLogoHash(stored.sha256().substring(0, LOGO_HASH_LENGTH));
        s.setLogoOriginalName(originalName == null ? null : truncate(originalName, 255));
        replaceFileAfterCompletion(previousKey, stored.key());
        repository.flush();
        return toResponse(s);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.COMPANY_SETTINGS, allEntries = true)
    public CompanySettingsResponse deleteLogo() {
        CompanySettings s = load();
        String previousKey = s.getLogoKey();
        s.setLogoKey(null);
        s.setLogoContentType(null);
        s.setLogoHash(null);
        s.setLogoOriginalName(null);
        replaceFileAfterCompletion(previousKey, null);
        repository.flush();
        return toResponse(s);
    }

    /** The logo to serve publicly; 404 when there is none. */
    @Transactional(readOnly = true)
    public LogoFile loadLogo() {
        CompanySettings s = load();
        if (s.getLogoKey() == null) {
            throw new NotFoundException("settings.logo.not-found");
        }
        return new LogoFile(storage.load(s.getLogoKey()), s.getLogoContentType(), s.getLogoHash());
    }

    /** {@code "2026.3"} → {@code "2026.4"}; a new year restarts at {@code .1}. */
    static String nextVersion(String current, int year) {
        String[] parts = current == null ? new String[0] : current.split("\\.");
        if (parts.length == 2 && parts[0].equals(String.valueOf(year))) {
            try {
                return year + "." + (Integer.parseInt(parts[1]) + 1);
            } catch (NumberFormatException e) {
                // falls through to a fresh version
            }
        }
        return year + ".1";
    }

    private CompanySettings load() {
        return repository.findById(CompanySettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("company_settings row missing (seed migration)"));
    }

    /** After commit the old file is deleted; after rollback the new one is, so no orphan is left. */
    private void replaceFileAfterCompletion(String previousKey, String newKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String obsolete = status == STATUS_COMMITTED ? previousKey : newKey;
                if (obsolete != null && !Objects.equals(previousKey, newKey)) {
                    try {
                        storage.delete(obsolete);
                    } catch (RuntimeException e) {
                        log.warn("Could not delete stored file {}", obsolete, e);
                    }
                }
            }
        });
    }

    private static String logoUrl(CompanySettings s) {
        return s.getLogoHash() == null ? null : LOGO_PATH + "?v=" + s.getLogoHash();
    }

    private static CompanySettingsResponse toResponse(CompanySettings s) {
        return new CompanySettingsResponse(s.getName(), s.getLegalName(), s.getNif(), s.getEmail(), s.getPhone(), s.getAddress(), s.getWebsite(),
                logoUrl(s), s.getDataRetentionMonths(), s.getConsentTextVersion(), s.getConsentJobText(), s.getConsentTalentPoolText(), s.getUpdatedAt());
    }

    private static BusinessException invalidLogo(String key) {
        return new BusinessException(key, List.of(new FieldError("file", key)));
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
