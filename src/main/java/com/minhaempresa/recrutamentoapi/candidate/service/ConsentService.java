package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.ConsentResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Consent;
import com.minhaempresa.recrutamentoapi.candidate.entity.ConsentType;
import com.minhaempresa.recrutamentoapi.candidate.mapper.CandidateMapper;
import com.minhaempresa.recrutamentoapi.candidate.repository.ConsentRepository;
import com.minhaempresa.recrutamentoapi.settings.service.CompanySettingsService;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consents (Lei n.º 22/11). Each grant is a new row with the version of the consent text in force, so
 * it is possible to prove what each person accepted; revoking stamps the date and never deletes. The
 * talent-pool consent decides who appears in the talent pool. Job-application consents arrive with the
 * applications (stage 8).
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository repository;
    private final CandidateService candidateService;
    private final CompanySettingsService companySettings;
    private final CandidateMapper mapper;
    private final Clock clock;

    /** History of the own consents, most recent first. */
    @Transactional
    public List<ConsentResponse> listMine(AuthenticatedUser actor) {
        return list(candidateService.ownCandidateId(actor));
    }

    /** Grants or revokes the talent-pool or marketing consent; returns the history. */
    @Transactional
    public List<ConsentResponse> setMine(AuthenticatedUser actor, ConsentType type, boolean granted) {
        if (type == ConsentType.JOB_APPLICATION) {
            throw new BusinessException("consent.type.invalid", List.of(new FieldError("type", "consent.type.invalid")));
        }
        UUID candidateId = candidateService.ownCandidateId(actor);
        var active = repository.findByCandidateIdAndTypeAndJobIdIsNullAndRevokedAtIsNull(candidateId, type);
        if (granted && active.isEmpty()) {
            repository.save(new Consent(candidateId, type, null, companySettings.get().consentTextVersion(), clock.instant()));
        }
        if (!granted) {
            active.ifPresent(consent -> consent.revoke(clock.instant()));
        }
        if (type == ConsentType.TALENT_POOL) {
            candidateService.setInTalentPool(candidateId, granted);
        }
        repository.flush();
        return list(candidateId);
    }

    /**
     * Consents given with an application (stage 8): the job-application consent for that job (required) with
     * the consent text version in force, and the talent-pool consent when asked and not active yet (a new
     * history row; the talent-pool mirror follows). Never revokes anything.
     */
    @Transactional
    public void recordApplicationConsents(UUID candidateId, UUID jobId, boolean talentPool) {
        String version = companySettings.get().consentTextVersion();
        repository.save(new Consent(candidateId, ConsentType.JOB_APPLICATION, jobId, version, clock.instant()));
        if (talentPool && repository.findByCandidateIdAndTypeAndJobIdIsNullAndRevokedAtIsNull(candidateId, ConsentType.TALENT_POOL).isEmpty()) {
            repository.save(new Consent(candidateId, ConsentType.TALENT_POOL, null, version, clock.instant()));
            candidateService.setInTalentPool(candidateId, true);
        }
        repository.flush();
    }

    /** Whether the candidate has an active (not revoked) general consent of this type (talent pool, marketing). */
    @Transactional(readOnly = true)
    public boolean hasActive(UUID candidateId, ConsentType type) {
        return repository.findByCandidateIdAndTypeAndJobIdIsNullAndRevokedAtIsNull(candidateId, type).isPresent();
    }

    /**
     * Text version of the active job-application consent the candidate gave for this job (stage 8c: whether it
     * covers sharing with the client). Empty when there is none (e.g. a talent-pool candidate added by a recruiter).
     */
    @Transactional(readOnly = true)
    public Optional<String> activeJobApplicationVersion(UUID candidateId, UUID jobId) {
        return repository.findByCandidateIdAndTypeAndJobIdAndRevokedAtIsNullOrderByGrantedAtDesc(candidateId, ConsentType.JOB_APPLICATION, jobId).stream()
                .findFirst()
                .map(Consent::getTextVersion);
    }

    private List<ConsentResponse> list(UUID candidateId) {
        return repository.findByCandidateIdOrderByGrantedAtDescIdDesc(candidateId).stream().map(mapper::toConsentResponse).toList();
    }
}
