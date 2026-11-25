package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.dto.request.MyApplicationRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.PublicApplicationRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.QuickProfileRequest;
import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationCreatedResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationReceivedResponse;
import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.QuickProfile;
import com.minhaempresa.recrutamentoapi.application.entity.RequirementMatchEntry;
import com.minhaempresa.recrutamentoapi.application.entity.StageMove;
import com.minhaempresa.recrutamentoapi.application.entity.StageMoveAction;
import com.minhaempresa.recrutamentoapi.application.event.ApplicationSubmittedEvent;
import com.minhaempresa.recrutamentoapi.application.event.DuplicateApplicationEvent;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.application.repository.StageMoveRepository;
import com.minhaempresa.recrutamentoapi.auth.service.RateLimiter;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import com.minhaempresa.recrutamentoapi.candidate.service.ApplicantData;
import com.minhaempresa.recrutamentoapi.candidate.service.ApplicantProfile;
import com.minhaempresa.recrutamentoapi.candidate.service.ApplicantQuickProfile;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.ConsentService;
import com.minhaempresa.recrutamentoapi.candidate.service.DocumentService;
import com.minhaempresa.recrutamentoapi.candidate.service.MatchProfile;
import com.minhaempresa.recrutamentoapi.candidate.service.MatchScorer;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import com.minhaempresa.recrutamentoapi.job.service.ApplicableJob;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applying to a job (stage 8a): the public form (no account) and the logged-in candidate.
 *
 * <ul>
 *   <li><b>Public form:</b> when the job is publicly visible the answer is always the same (202
 *       {@code {status: "received"}}). If the email already applied to this job nothing is stored and that
 *       email is told so (no disclosure of applications, adjustment 2). Honeypot {@code website}, per-IP
 *       (generous, CGNAT) and per-email limits; the IP is never stored. A job that is not visible → 404.
 *   <li><b>Logged-in candidate:</b> the own profile gives the name, email, phone and province; a duplicate →
 *       409; a job that stopped accepting applications (paused, closed, deadline passed) → 422.
 *   <li>The candidate is found by email or created ({@link CandidateService#findOrCreateForApplication}); an
 *       existing profile is never changed. Applications of one email are serialized by an advisory lock, so
 *       two at the same time never create two candidates nor two applications.
 *   <li>The CV (5 MB, PDF/DOCX/PNG/JPEG by content, virus scan) becomes a document of the candidate linked to
 *       the application; it is the profile's current CV only for a profile created now (D8-2).
 *   <li>Consents: job application (with the text version in force) and, when asked, talent pool.
 *   <li>Score and requirement matches computed once (D8-3): logged-in candidate = stored profile + quick profile and
 *       province sent; anonymous form = ONLY the quick profile and province sent (never the stored profile of the
 *       email owner). The "already applied" email goes at most once per day per (email, job).
 *   <li>{@code eliminated} from the eliminatory questions, never shown to the candidate.
 *   <li>After the commit: the automatic action of the "Nova" stage; in the transaction: the bell of the job's
 *       recruiter (D8-4).
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final String CV_FIELD = "cv";
    private static final Duration DUPLICATE_NOTICE_INTERVAL = Duration.ofDays(1);

    private final ApplicationRepository repository;
    private final StageMoveRepository stageMoves;
    private final JobService jobService;
    private final CandidateService candidateService;
    private final DocumentService documentService;
    private final ConsentService consentService;
    private final MatchScorer matchScorer;
    private final RateLimiter rateLimiter;
    private final ApplicationProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** An uploaded CV (the part {@code cv}); {@code content} null when none was sent. */
    public record CvUpload(String originalName, byte[] content) {

        public static final CvUpload NONE = new CvUpload(null, null);

        boolean present() {
            return content != null && content.length > 0;
        }

        @Override
        public String toString() {
            return "CvUpload";
        }
    }

    /**
     * What the application copies: sent by the public form or taken from the logged-in candidate's profile.
     *
     * @param loggedIn only then may the stored profile count in the score
     */
    private record Applicant(UUID candidateId, boolean newCandidate, boolean loggedIn, String fullName, String email, String phone, Province province,
            UUID profileCvId) {}

    // =====================================================================================
    // Public form
    // =====================================================================================

    @Transactional
    public ApplicationReceivedResponse applyPublic(PublicApplicationRequest request, CvUpload cv, String ipAddress) {
        if (Texts.blankToNull(request.website()) != null) {
            log.info("Application form honeypot filled: request discarded");
            return ApplicationReceivedResponse.RECEIVED;
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        rateLimiter.check("application-per-ip", ipAddress, properties.rateLimits().perIp());
        rateLimiter.check("application-per-email", email, properties.rateLimits().perEmail());

        ApplicableJob job = jobService.findApplicable(request.jobId())
                .filter(ApplicableJob::publiclyVisible)
                .orElseThrow(() -> new NotFoundException("job.not-found"));
        ScreeningAnswers.Result answers = ScreeningAnswers.check(job.questions(), request.screeningAnswers());
        requireCvOrQuickProfile(cv, request.quickProfile(), false);
        String phone = PhoneNumbers.normalizeOrThrow(request.phone());

        candidateService.lockApplicantEmail(email);
        Optional<UUID> existing = candidateService.findIdByEmail(email);
        if (existing.isPresent() && repository.existsByJobIdAndCandidateId(job.id(), existing.get())) {
            notifyDuplicate(email, job);
            return ApplicationReceivedResponse.RECEIVED;
        }
        ApplicantQuickProfile quick = toApplicantQuickProfile(request.quickProfile());
        CandidateService.ApplicantLink link = candidateService.findOrCreateForApplication(
                new ApplicantData(request.fullName(), email, phone, request.province(), sourceOf(request.source()), quick));
        Applicant applicant = new Applicant(link.candidateId(), link.created(), false, request.fullName().trim(), email, phone, request.province(), null);
        create(job, applicant, cv, request.quickProfile(), answers, request.consentTalentPool(), sourceOf(request.source()));
        return ApplicationReceivedResponse.RECEIVED;
    }

    // =====================================================================================
    // Logged-in candidate
    // =====================================================================================

    @Transactional
    public ApplicationCreatedResponse applyMine(AuthenticatedUser actor, MyApplicationRequest request, CvUpload cv) {
        ApplicantProfile me = candidateService.ownApplicant(actor);
        rateLimiter.check("application-per-email", me.email(), properties.rateLimits().perEmail());

        ApplicableJob job = jobService.findApplicable(request.jobId())
                .filter(j -> j.status() != JobStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("job.not-found"));
        if (!job.publiclyVisible()) {
            throw new BusinessException("application.job-not-open");
        }
        ScreeningAnswers.Result answers = ScreeningAnswers.check(job.questions(), request.screeningAnswers());
        requireCvOrQuickProfile(cv, request.quickProfile(), true);

        candidateService.lockApplicantEmail(me.email());
        if (repository.existsByJobIdAndCandidateId(job.id(), me.candidateId())) {
            throw duplicate();
        }
        Applicant applicant = new Applicant(me.candidateId(), false, true, me.fullName(), me.email(), me.phone(), me.province(), me.cvDocumentId());
        Application application = create(job, applicant, cv, request.quickProfile(), answers, request.consentTalentPool(), sourceOf(request.source()));
        return new ApplicationCreatedResponse(application.getId());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private Application create(ApplicableJob job, Applicant applicant, CvUpload cv, QuickProfileRequest quick, ScreeningAnswers.Result answers,
            boolean talentPool, CandidateSource source) {
        Instant now = clock.instant();
        UUID cvId = cv.present()
                ? documentService.storeApplicationCv(applicant.candidateId(), cv.originalName(), cv.content(), applicant.newCandidate(), CV_FIELD)
                : applicant.profileCvId();
        consentService.recordApplicationConsents(applicant.candidateId(), job.id(), talentPool);

        MatchResultResponse match = matchScorer.score(job.requirements(), matchProfile(applicant, quick));
        Application application = new Application(job.id(), applicant.candidateId(), PipelineStageKey.NEW, source, match.score(), answers.eliminated(), now);
        application.setFullName(applicant.fullName());
        application.setEmail(applicant.email());
        application.setPhone(applicant.phone());
        application.setProvince(applicant.province());
        application.setCvDocumentId(cvId);
        application.setQuickProfile(toQuickProfile(quick));
        application.getAnswers().addAll(answers.answers());
        match.matches().forEach(m -> application.getMatches().add(new RequirementMatchEntry(m.requirementId(), m.met(), m.detail())));
        try {
            repository.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            // Same job and candidate at the same instant despite the lock (e.g. another email of the same profile)
            throw duplicate();
        }
        stageMoves.save(new StageMove(application.getId(), StageMoveAction.APPLIED, null, PipelineStageKey.NEW, null, null, now));
        events.publishEvent(new ApplicationSubmittedEvent(application.getId(), job.id(), job.title(), job.recruiterId(), applicant.candidateId(), applicant.fullName()));
        return application;
    }

    /**
     * "You already applied" email, at most once per day per (email, job): the next duplicates still get the
     * neutral answer but no email. The claim is atomic in the database (several instances); only a hash of
     * email and job is stored.
     */
    private void notifyDuplicate(String email, ApplicableJob job) {
        Instant now = clock.instant();
        if (!repository.claimDuplicateNotice(duplicateKey(email, job.id()), now, now.minus(DUPLICATE_NOTICE_INTERVAL)).isEmpty()) {
            events.publishEvent(new DuplicateApplicationEvent(email, job.title()));
        }
    }

    /** Daily cleanup of the duplicate-notice marks older than the interval. */
    @Scheduled(cron = "${app.applications.cleanup-cron:0 30 3 * * *}", zone = "${app.defaults.time-zone}")
    @Transactional
    public int deleteOldDuplicateNotices() {
        return repository.deleteDuplicateNoticesBefore(clock.instant().minus(DUPLICATE_NOTICE_INTERVAL));
    }

    private static String duplicateKey(String email, UUID jobId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((email + "|" + jobId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * What counts in the score (never age, gender, nationality nor photo). A logged-in candidate: the stored
     * profile, completed with the quick profile and province sent. An anonymous application: ONLY what it sent
     * (quick profile and province), also when the email belongs to an existing candidate: whoever types an
     * email must not be scored with that person's stored profile.
     */
    private MatchProfile matchProfile(Applicant applicant, QuickProfileRequest quick) {
        MatchProfile profile = applicant.loggedIn()
                ? candidateService.matchProfileOf(applicant.candidateId())
                : new MatchProfile(List.of(), 0, List.of(), List.of(), List.of(), null);
        Province province = applicant.province() != null ? applicant.province() : profile.province();
        if (quick == null) {
            return new MatchProfile(profile.skills(), profile.totalExperienceYears(), profile.educationLevels(), profile.languages(), profile.driverLicenses(), province);
        }
        Map<String, String> skills = new LinkedHashMap<>();
        profile.skills().forEach(s -> skills.putIfAbsent(Texts.fold(s), s));
        quick.skills().forEach(s -> skills.putIfAbsent(Texts.fold(s.trim()), s.trim()));
        List<EducationLevel> education = new ArrayList<>(profile.educationLevels());
        education.add(quick.educationLevel());
        List<String> licenses = new ArrayList<>(profile.driverLicenses());
        licensesOf(quick).stream().filter(l -> !licenses.contains(l)).forEach(licenses::add);
        return new MatchProfile(List.copyOf(skills.values()), Math.max(profile.totalExperienceYears(), quick.years()), education, profile.languages(), licenses, province);
    }

    /** A CV or the quick profile; a logged-in candidate may rely on the own profile instead. */
    private void requireCvOrQuickProfile(CvUpload cv, QuickProfileRequest quick, boolean loggedIn) {
        if (cv.present()) {
            documentService.checkApplicationCv(cv.content(), CV_FIELD);
        } else if (quick == null && !loggedIn) {
            throw new BusinessException("application.cv.required", List.of(new FieldError(CV_FIELD, "application.cv.required")));
        }
    }

    private static ApplicantQuickProfile toApplicantQuickProfile(QuickProfileRequest quick) {
        if (quick == null) {
            return null;
        }
        return new ApplicantQuickProfile(quick.headline().trim(), quick.skills().stream().map(String::trim).toList(), quick.years(), quick.educationLevel(),
                Texts.blankToNull(quick.lastRole()), Texts.blankToNull(quick.lastCompany()), quick.driverLicenses() == null ? List.of() : quick.driverLicenses());
    }

    private static QuickProfile toQuickProfile(QuickProfileRequest quick) {
        if (quick == null) {
            return null;
        }
        return new QuickProfile(quick.headline().trim(), quick.skills().stream().map(String::trim).toList(), quick.years(), quick.educationLevel(),
                trimmedOrNull(quick.lastRole()), trimmedOrNull(quick.lastCompany()), licensesOf(quick));
    }

    private static List<String> licensesOf(QuickProfileRequest quick) {
        return quick.driverLicenses() == null ? List.of() : quick.driverLicenses().stream().distinct().sorted().map(Enum::name).toList();
    }

    private static String trimmedOrNull(String value) {
        String text = Texts.blankToNull(value);
        return text == null ? null : text.trim();
    }

    private static CandidateSource sourceOf(CandidateSource source) {
        return source == null ? CandidateSource.PORTAL : source;
    }

    private static ConflictException duplicate() {
        return new ConflictException("application.duplicate", List.of(new FieldError("jobId", "application.duplicate")));
    }
}
