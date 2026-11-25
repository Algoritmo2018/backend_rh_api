package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.CandidateUpdateRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.EducationRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.LanguageSkillRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.WorkExperienceRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateProfileResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSearchHitResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateLanguage;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.Education;
import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;
import com.minhaempresa.recrutamentoapi.candidate.entity.WorkExperience;
import com.minhaempresa.recrutamentoapi.candidate.mapper.CandidateMapper;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentResponse;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.DocumentRepository;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobRequirementResponse;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.CandidateAccount;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate profiles. The owner edits its own profile ({@code /me}, found by the user id of the access
 * token, never by an id sent by the client); staff read every profile; interviewers only the candidates
 * assigned to them, without private data (decision D6-1). An application never changes an existing
 * profile.
 */
@Service
@RequiredArgsConstructor
public class CandidateService {

    private static final int QUICK_SEARCH_LIMIT = 5;
    private static final double DAYS_PER_MONTH = 30.44;

    private final CandidateRepository repository;
    private final DocumentRepository documentRepository;
    private final CandidateMapper mapper;
    private final UserService userService;
    private final CandidateAccessPolicy accessPolicy;
    private final MatchScorer matchScorer;
    private final AppProperties appProperties;
    private final Clock clock;

    // =====================================================================================
    // Own profile (candidate portal)
    // =====================================================================================

    @Transactional
    public CandidateResponse getMine(AuthenticatedUser actor) {
        return mapper.toResponse(own(actor));
    }

    /** Partial edit: absent fields stay; lists, when sent, are complete. {@code email} is ignored. */
    @Transactional
    public CandidateResponse updateMine(AuthenticatedUser actor, CandidateUpdateRequest request) {
        Candidate candidate = own(actor);
        LocalDate today = today();
        if (request.fullName() != null) {
            candidate.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            candidate.setPhone(PhoneNumbers.normalizeOrThrow(request.phone()));
        }
        if (request.whatsapp() != null) {
            String whatsapp = Texts.blankToNull(request.whatsapp());
            if (whatsapp != null && !PhoneNumbers.isValid(whatsapp)) {
                throw fieldError("whatsapp", "validation.phone.invalid");
            }
            candidate.setWhatsapp(whatsapp == null ? null : PhoneNumbers.normalizeOrThrow(whatsapp));
        }
        if (request.gender() != null) {
            candidate.setGender(request.gender());
        }
        if (request.birthDate() != null) {
            int age = Period.between(request.birthDate(), today).getYears();
            if (age < 16 || age > 80) {
                throw fieldError("birthDate", "candidate.birth-date.range");
            }
            candidate.setBirthDate(request.birthDate());
        }
        if (request.nationality() != null) {
            candidate.setNationality(request.nationality().trim());
        }
        if (request.isAngolan() != null) {
            candidate.setAngolan(request.isAngolan());
        }
        if (request.province() != null) {
            candidate.setProvince(request.province());
        }
        if (request.city() != null) {
            candidate.setCity(request.city().trim());
        }
        if (request.headline() != null) {
            candidate.setHeadline(request.headline().trim());
        }
        if (request.summary() != null) {
            candidate.setSummary(Texts.blankToNull(request.summary()));
        }
        if (request.experience() != null) {
            replaceExperience(candidate, request.experience(), YearMonth.from(today));
        }
        if (request.education() != null) {
            replaceEducation(candidate, request.education(), today.getYear());
        }
        if (request.skills() != null) {
            candidate.setSkills(distinctIgnoringCase(request.skills()));
        }
        if (request.languages() != null) {
            replaceLanguages(candidate, request.languages());
        }
        if (request.driverLicenses() != null) {
            candidate.setDriverLicenses(request.driverLicenses().stream().distinct().sorted().map(DriverLicenseCategory::name).collect(Collectors.toCollection(ArrayList::new)));
        }
        if (request.availability() != null) {
            candidate.setAvailability(request.availability());
        }
        if (request.salaryExpectation() != null) {
            candidate.setSalaryExpectation(request.salaryExpectation().amount() == null
                    ? null
                    : new Money(request.salaryExpectation().amount(), request.salaryExpectation().currency()));
        }
        refreshDerivedFields(candidate, today);
        repository.flush();
        return mapper.toResponse(candidate);
    }

    /** Id of the own profile (created or linked on the fly for a confirmed account). */
    @Transactional
    public UUID ownCandidateId(AuthenticatedUser actor) {
        return own(actor).getId();
    }

    /** Match score of the own profile for a job (recommended jobs). */
    @Transactional(readOnly = true)
    public MatchResultResponse match(UUID candidateId, List<JobRequirementResponse> requirements) {
        return matchScorer.score(requirements, matchProfile(find(candidateId)));
    }

    // =====================================================================================
    // Staff
    // =====================================================================================

    /**
     * Candidate page. Administrators and recruiters see everything; an interviewer only a candidate
     * assigned to them (otherwise 403 "Este candidato não lhe foi atribuído."), without private data.
     */
    @Transactional(readOnly = true)
    public CandidateProfileResponse profile(UUID id, AuthenticatedUser viewer) {
        boolean restricted = viewer.hasRole(UserRole.INTERVIEWER.name());
        if (restricted && !accessPolicy.interviewerMaySee(viewer.id(), id)) {
            throw new ForbiddenException("candidate.not-assigned");
        }
        if (!restricted && !viewer.hasRole(UserRole.ADMIN.name()) && !viewer.hasRole(UserRole.RECRUITER.name())) {
            throw new ForbiddenException();
        }
        Candidate entity = find(id);
        CandidateResponse candidate = mapper.toResponse(entity);
        DocumentResponse cv = entity.getCvDocumentId() == null
                ? null
                : documentRepository.findById(entity.getCvDocumentId()).map(mapper::toDocumentResponse).orElse(null);
        return new CandidateProfileResponse(restricted ? candidate.withoutPrivateData() : candidate, cv, List.of(), restricted);
    }

    /** Top-bar search: name, headline, email or phone. Interviewers only find their candidates. */
    @Transactional(readOnly = true)
    public List<CandidateSearchHitResponse> quickSearch(String term, AuthenticatedUser viewer) {
        String pattern = "%" + Texts.escapeLike(term.trim()) + "%";
        if (!viewer.hasRole(UserRole.INTERVIEWER.name())) {
            return load(repository.quickSearch(pattern, QUICK_SEARCH_LIMIT)).stream().map(c -> mapper.toSearchHit(c, true)).toList();
        }
        Set<UUID> visible = accessPolicy.visibleToInterviewer(viewer.id());
        if (visible.isEmpty()) {
            return List.of();
        }
        return load(repository.quickSearch(pattern, 200)).stream()
                .filter(c -> visible.contains(c.getId()))
                .limit(QUICK_SEARCH_LIMIT)
                .map(c -> mapper.toSearchHit(c, false))
                .toList();
    }

    /** Every skill written in a profile (suggestions of the skill inputs and filters). */
    @Transactional(readOnly = true)
    public List<String> skillSuggestions() {
        return repository.findDistinctSkills();
    }

    // =====================================================================================
    // Support for other modules (never exposed by a controller)
    // =====================================================================================

    /**
     * The candidate of an application (stage 8), by email. An existing candidate is only linked: the
     * application NEVER changes its profile (name, phone, CV…); what was sent stays in the application.
     * A new candidate is created from the application data (and its quick profile, if any).
     *
     * <p>Two first applications with the same new email at the same time: the email is locked for the rest of
     * the transaction ({@link #lockApplicantEmail}), so the second waits and links to the candidate the first
     * one created instead of hitting the unique email.
     */
    @Transactional
    public ApplicantLink findOrCreateForApplication(ApplicantData data) {
        String email = lockApplicantEmail(data.email());
        return repository.findByEmail(email)
                .map(existing -> new ApplicantLink(existing.getId(), false))
                .orElseGet(() -> {
                    Candidate created = newCandidate(data.fullName().trim(), email, PhoneNumbers.normalizeOrThrow(data.phone()), data.source());
                    created.setProvince(data.province());
                    ApplicantQuickProfile quick = data.quickProfile();
                    if (quick != null) {
                        created.setHeadline(quick.headline() == null ? "" : quick.headline().trim());
                        created.setSkills(distinctIgnoringCase(quick.skills() == null ? List.of() : quick.skills()));
                        created.setDriverLicenses(quick.driverLicenses() == null ? new ArrayList<>()
                                : quick.driverLicenses().stream().distinct().sorted().map(DriverLicenseCategory::name).collect(Collectors.toCollection(ArrayList::new)));
                    }
                    refreshDerivedFields(created, today());
                    repository.save(created);
                    repository.flush();
                    return new ApplicantLink(created.getId(), true);
                });
    }

    /**
     * Serializes the applications (and the account confirmations) of one email until the end of the current
     * transaction (PostgreSQL advisory lock). Returns the email in lower case.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String lockApplicantEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        repository.lockApplicantEmail(normalized);
        return normalized;
    }

    /** The candidate with this email (lower case), if any. */
    @Transactional(readOnly = true)
    public Optional<UUID> findIdByEmail(String email) {
        return repository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(Candidate::getId);
    }

    /** The own profile of a logged-in candidate who applies (409 while the email is not confirmed). */
    @Transactional
    public ApplicantProfile ownApplicant(AuthenticatedUser actor) {
        Candidate candidate = own(actor);
        return new ApplicantProfile(candidate.getId(), candidate.getFullName(), candidate.getEmail(), candidate.getPhone(),
                candidate.getProvince(), candidate.getCvDocumentId());
    }

    /** What the match score may use of a profile (DECISOES.md #20). */
    @Transactional(readOnly = true)
    public MatchProfile matchProfileOf(UUID candidateId) {
        return matchProfile(find(candidateId));
    }

    /** Whether the candidate is linked to a portal account (an account email cannot be an anonymous applicant's). */
    @Transactional(readOnly = true)
    public Optional<UUID> userIdOf(UUID candidateId) {
        return Optional.ofNullable(find(candidateId).getUserId());
    }

    /** Candidate of an application: {@code created} = a new profile was made. */
    public record ApplicantLink(UUID candidateId, boolean created) {}

    /**
     * Called when a candidate account confirmed its email (decision D6-2): links the candidate that
     * already has this email (made by applications without an account) or creates an empty profile.
     */
    @Transactional
    public UUID linkOrCreateForAccount(UUID userId, String email, String name, String phone) {
        Candidate candidate = repository.findByUserId(userId).orElse(null);
        if (candidate == null) {
            // Same lock as the applications: an application with this email at the same time cannot create a second profile
            email = lockApplicantEmail(email);
            candidate = repository.findByEmail(email).orElse(null);
            if (candidate != null && candidate.getUserId() != null) {
                // Emails are unique per account: another account cannot own this candidate
                throw new ConflictException("candidate.email-taken");
            }
            if (candidate == null) {
                candidate = newCandidate(name.trim(), email, phone, CandidateSource.PORTAL);
                refreshDerivedFields(candidate, today());
                repository.save(candidate);
            }
            candidate.setUserId(userId);
            repository.flush();
        }
        userService.linkCandidate(userId, candidate.getId());
        return candidate.getId();
    }

    /** Mirror of the talent-pool consent (consents service only). */
    @Transactional
    public void setInTalentPool(UUID candidateId, boolean inTalentPool) {
        find(candidateId).setInTalentPool(inTalentPool);
    }

    // =====================================================================================
    // Contacts (messages module)
    // =====================================================================================

    /** @throws NotFoundException if there is no such candidate */
    @Transactional(readOnly = true)
    public CandidateContact getContact(UUID candidateId) {
        return toContact(find(candidateId));
    }

    /** Contacts by id (missing ids are left out). */
    @Transactional(readOnly = true)
    public Map<UUID, CandidateContact> contactsOf(Collection<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(Set.copyOf(candidateIds)).stream().collect(Collectors.toMap(Candidate::getId, CandidateService::toContact));
    }

    /** Ids of candidates whose name contains the term (accents and case ignored), at most {@code limit}. */
    @Transactional(readOnly = true)
    public List<UUID> idsByName(String term, int limit) {
        String escaped = term.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return escaped.isEmpty() ? List.of() : repository.findIdsByNameContaining("%" + escaped + "%", limit);
    }

    private static CandidateContact toContact(Candidate c) {
        return new CandidateContact(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getWhatsapp());
    }

    /** Candidates by id, in the given order (missing ids are skipped). Entities: this module only. */
    List<Candidate> load(Collection<UUID> ids) {
        Map<UUID, Candidate> byId = repository.findAllById(ids).stream().collect(Collectors.toMap(Candidate::getId, c -> c));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * The profile of the authenticated candidate. A confirmed account without a profile (e.g. confirmed
     * before this module existed) gets it now; an unconfirmed one → 409.
     */
    private Candidate own(AuthenticatedUser actor) {
        CandidateAccount account = userService.findCandidateAccount(actor.id()).orElseThrow(ForbiddenException::new);
        return repository.findByUserId(account.userId()).orElseGet(() -> {
            if (!account.emailVerified()) {
                throw new ConflictException("candidate.email-not-verified");
            }
            return find(linkOrCreateForAccount(account.userId(), account.email(), account.name(), account.phone()));
        });
    }

    private Candidate newCandidate(String fullName, String email, String phone, CandidateSource source) {
        Candidate candidate = new Candidate(fullName, email, phone, appProperties.defaults().nationality(), source);
        String language = appProperties.defaults().language();
        candidate.addLanguage(language, Texts.fold(language), LanguageLevel.NATIVE);
        return candidate;
    }

    /** Child-collection rule: UUID of an experience of this candidate → update; anything else → new. */
    private static void replaceExperience(Candidate candidate, List<WorkExperienceRequest> items, YearMonth currentMonth) {
        Map<UUID, WorkExperience> existing = new HashMap<>();
        candidate.getExperience().forEach(e -> existing.put(e.getId(), e));
        int position = 0;
        for (WorkExperienceRequest item : items) {
            String path = "experience[" + position + "]";
            if (item.startDate().isAfter(currentMonth)) {
                throw fieldError(path + ".startDate", "candidate.experience.start-future");
            }
            if (!item.current() && item.endDate() == null) {
                throw fieldError(path + ".endDate", "candidate.experience.end-required");
            }
            if (!item.current() && item.endDate().isBefore(item.startDate())) {
                throw fieldError(path + ".endDate", "candidate.experience.end-before-start");
            }
            UUID id = parseUuid(item.id());
            WorkExperience experience = id == null ? null : existing.remove(id);
            if (experience == null) {
                experience = candidate.addExperience();
            }
            experience.setPosition(position++);
            experience.setCompany(item.company().trim());
            experience.setRole(item.role().trim());
            experience.setProvince(item.province());
            experience.setStartDate(item.startDate());
            experience.setCurrent(item.current());
            experience.setEndDate(item.current() ? null : item.endDate());
            experience.setDescription(Texts.blankToNull(item.description()));
        }
        candidate.getExperience().removeAll(existing.values());
        candidate.getExperience().sort(Comparator.comparingInt(WorkExperience::getPosition));
    }

    private static void replaceEducation(Candidate candidate, List<EducationRequest> items, int currentYear) {
        Map<UUID, Education> existing = new HashMap<>();
        candidate.getEducation().forEach(e -> existing.put(e.getId(), e));
        int position = 0;
        for (EducationRequest item : items) {
            String path = "education[" + position + "]";
            if (item.startYear() < 1960 || item.startYear() > currentYear) {
                throw fieldError(path + ".startYear", "candidate.education.start-year");
            }
            if (item.endYear() != null && (item.endYear() < item.startYear() || item.endYear() > currentYear + 6)) {
                throw fieldError(path + ".endYear", "candidate.education.end-year");
            }
            UUID id = parseUuid(item.id());
            Education education = id == null ? null : existing.remove(id);
            if (education == null) {
                education = candidate.addEducation();
            }
            education.setPosition(position++);
            education.setInstitution(item.institution().trim());
            education.setDegree(item.degree().trim());
            education.setLevel(item.level());
            education.setField(Texts.blankToNull(item.field()));
            education.setStartYear(item.startYear());
            education.setEndYear(item.endYear());
        }
        candidate.getEducation().removeAll(existing.values());
        candidate.getEducation().sort(Comparator.comparingInt(Education::getPosition));
    }

    /**
     * The list has no ids: a language already in the profile (same name, accents ignored) is updated,
     * so the unique (candidate, language) never collides; the others are added or removed.
     */
    private static void replaceLanguages(Candidate candidate, List<LanguageSkillRequest> items) {
        Map<String, CandidateLanguage> existing = new LinkedHashMap<>();
        candidate.getLanguages().forEach(l -> existing.put(l.getLanguageKey(), l));
        Set<String> seen = new LinkedHashSet<>();
        int position = 0;
        for (LanguageSkillRequest item : items) {
            String name = item.language().trim();
            String key = Texts.fold(name);
            if (!seen.add(key)) {
                throw fieldError("languages", "candidate.languages.duplicate");
            }
            CandidateLanguage language = existing.remove(key);
            if (language == null) {
                language = candidate.addLanguage(name, key, item.level());
            }
            language.setLanguage(name);
            language.setLevel(item.level());
            language.setPosition(position++);
        }
        candidate.getLanguages().removeAll(existing.values());
        candidate.getLanguages().sort(Comparator.comparingInt(CandidateLanguage::getPosition));
    }

    /** Years of experience (frontend {@code computeExperienceYears}) and the indexed search text. */
    private static void refreshDerivedFields(Candidate candidate, LocalDate today) {
        double months = 0;
        for (WorkExperience experience : candidate.getExperience()) {
            LocalDate start = experience.getStartDate().atDay(1);
            LocalDate end = experience.isCurrent() || experience.getEndDate() == null ? today : experience.getEndDate().atDay(1);
            months += Math.max(0, ChronoUnit.DAYS.between(start, end) / DAYS_PER_MONTH);
        }
        candidate.setTotalExperienceYears((int) Math.floor(months / 12));

        Stream<String> parts = Stream.of(
                Stream.of(candidate.getFullName(), candidate.getHeadline(), candidate.getSummary(), candidate.getCity(),
                        candidate.getProvince() == null ? null : candidate.getProvince().getValue()),
                candidate.getSkills().stream(),
                candidate.getExperience().stream().flatMap(e -> Stream.of(e.getRole(), e.getCompany())),
                candidate.getEducation().stream().flatMap(e -> Stream.of(e.getDegree(), e.getField())),
                candidate.getLanguages().stream().map(CandidateLanguage::getLanguage)).flatMap(s -> s);
        candidate.setSearchText(parts.filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" ")));
    }

    MatchProfile matchProfile(Candidate candidate) {
        return new MatchProfile(
                List.copyOf(candidate.getSkills()),
                candidate.getTotalExperienceYears(),
                candidate.getEducation().stream().map(Education::getLevel).toList(),
                candidate.getLanguages().stream().map(l -> new MatchProfile.Language(l.getLanguage(), l.getLevel())).toList(),
                List.copyOf(candidate.getDriverLicenses()),
                candidate.getProvince());
    }

    private static List<String> distinctIgnoringCase(List<String> values) {
        Map<String, String> unique = new LinkedHashMap<>();
        values.stream().map(String::trim).forEach(v -> unique.putIfAbsent(Texts.fold(v), v));
        return new ArrayList<>(unique.values());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(appProperties.defaults().timeZone()));
    }

    private Candidate find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("candidate.not-found"));
    }

    private static BusinessException fieldError(String field, String key) {
        return new BusinessException(key, List.of(new FieldError(field, key)));
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
