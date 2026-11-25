package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.dto.request.AddToJobRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationBulkMoveRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationMoveRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationRejectRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationUpdateRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationVersionRef;
import com.minhaempresa.recrutamentoapi.application.dto.request.ClientCommentRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ShareWithClientRequest;
import com.minhaempresa.recrutamentoapi.application.dto.response.ClientCommentResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.ShareResponse;
import com.minhaempresa.recrutamentoapi.application.entity.ClientComment;
import com.minhaempresa.recrutamentoapi.application.entity.ClientDecision;
import com.minhaempresa.recrutamentoapi.application.event.ApplicationsClosedEvent;
import com.minhaempresa.recrutamentoapi.application.event.CandidatesSharedEvent;
import com.minhaempresa.recrutamentoapi.application.event.ClientCommentAddedEvent;
import com.minhaempresa.recrutamentoapi.application.repository.ClientCommentRepository;
import com.minhaempresa.recrutamentoapi.candidate.service.DocumentService;
import com.minhaempresa.recrutamentoapi.settings.service.CompanySettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.AddToJobResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.BulkMoveResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.PipelineCandidateResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.PipelineCardResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.RejectResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.StageMoveResponse;
import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus;
import com.minhaempresa.recrutamentoapi.application.entity.BulkAction;
import com.minhaempresa.recrutamentoapi.application.entity.RequirementMatchEntry;
import com.minhaempresa.recrutamentoapi.application.entity.StageMove;
import com.minhaempresa.recrutamentoapi.application.entity.StageMoveAction;
import com.minhaempresa.recrutamentoapi.application.mapper.ApplicationMapper;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.application.repository.BulkActionRepository;
import com.minhaempresa.recrutamentoapi.application.repository.StageMoveRepository;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.ConsentType;
import com.minhaempresa.recrutamentoapi.candidate.service.ApplicantProfile;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateCard;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.ConsentService;
import com.minhaempresa.recrutamentoapi.candidate.service.MatchScorer;
import com.minhaempresa.recrutamentoapi.job.service.ApplicableJob;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageTemplateResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageTemplateService;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PipelineStageResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.RejectionReasonResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.StageAutoActionResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.StageAutoActionType;
import com.minhaempresa.recrutamentoapi.settings.service.PipelineStageService;
import com.minhaempresa.recrutamentoapi.settings.service.RejectionReasonService;
import com.minhaempresa.recrutamentoapi.settings.service.TagService;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.exception.TooManyRequestsException;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pipeline of a job (stage 8b): list, history, move, reject, restore, edit and add talent-pool candidates.
 *
 * <ul>
 *   <li><b>Who:</b> administrators and recruiters act on every job. An interviewer only reads the applications of
 *       the jobs where they are assigned, in the restricted view (no email, phone nor photo, D6-1); another job →
 *       403 "Esta candidatura não lhe foi atribuída." (adjustment 6).
 *   <li><b>Paused and closed jobs</b> (adjustment 4): moving, rejecting, restoring and editing stay allowed; only
 *       adding talent-pool candidates needs a job that accepts applications (422 {@code application.job-not-open}).
 *   <li><b>Move:</b> with the {@code version} read (stale → 409). Rejected/withdrawn → 422 "Reponha a candidatura
 *       antes de a mover."; the final stage makes it hired (D8-6); entering the client interview marks it shared
 *       and approved. Optional automatic action of the target stage (or another template).
 *   <li><b>Messages</b> of the pipeline (automatic action, rejection email) are {@code automatic} with
 *       {@code sent_by} = the user who acted (adjustment 1): not counted in the sending limits; a failed email goes
 *       to that user's bell. Sent in the same transaction (outbox): a message that cannot be built (e.g. a variable
 *       without a value) → 422 and nothing changes. One email per candidate, never several recipients
 *       (adjustment 5).
 *   <li><b>Sharing with the client</b> (stage 8c): per application, only when the consent text version the candidate
 *       accepted for that job covers it (entering the client interview shares it too, approved, under the same rule);
 *       the client sees it while it is in progress or hired. FEH OIRAM comments and the "CV para o cliente" (the only
 *       file the client may open). Notices to the client never carry the candidate's name.
 *   <li><b>Bulk:</b> at most {@code app.applications.bulk.max-items} per request (422); a request with more than
 *       one application/candidate counts as a bulk action: {@code max-actions-per-day} per user in 24 hours,
 *       counted in the database (429 with {@code Retry-After}). Accepted race: two requests at the same instant
 *       may exceed the daily limit by one (same as the message limits).
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PipelineService {

    private static final Duration BULK_WINDOW = Duration.ofDays(1);
    private static final String REJECTION_TEMPLATE = "rejection-email";

    private final ApplicationRepository repository;
    private final StageMoveRepository stageMoves;
    private final BulkActionRepository bulkActions;
    private final ApplicationMapper mapper;
    private final JobService jobService;
    private final CandidateService candidateService;
    private final ConsentService consentService;
    private final MatchScorer matchScorer;
    private final MessageService messageService;
    private final MessageTemplateService messageTemplateService;
    private final PipelineStageService pipelineStageService;
    private final RejectionReasonService rejectionReasonService;
    private final TagService tagService;
    private final UserService userService;
    private final ClientCommentRepository clientComments;
    private final CompanySettingsService companySettings;
    private final DocumentService documentService;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<InterviewerApplicationAccess> interviewAccess;
    private final ApplicationProperties properties;
    private final Messages messages;
    private final Clock clock;

    // =====================================================================================
    // Reads
    // =====================================================================================

    /** Pipeline of a job, rejected ones included: best score first, applications without a score at the end. */
    @Transactional(readOnly = true)
    public List<PipelineCardResponse> listByJob(UUID jobId, AuthenticatedUser viewer) {
        ApplicableJob job = jobService.findApplicable(jobId).orElseThrow(() -> new NotFoundException("job.not-found"));
        boolean restricted = checkViewer(viewer, job, "job.not-assigned");
        return cards(repository.findPipeline(jobId), restricted);
    }

    @Transactional(readOnly = true)
    public PipelineCardResponse get(UUID id, AuthenticatedUser viewer) {
        Application application = find(id);
        boolean restricted = checkApplicationViewer(viewer, application);
        return card(application, restricted);
    }

    /** History of an application, the newest first, with the names of who acted. */
    @Transactional(readOnly = true)
    public List<StageMoveResponse> history(UUID id, AuthenticatedUser viewer) {
        Application application = find(id);
        checkApplicationViewer(viewer, application);
        List<StageMove> moves = stageMoves.findByApplicationIdOrderByCreatedAtDescIdDesc(id);
        Map<UUID, String> names = userService.namesOf(moves.stream().map(StageMove::getByUserId).filter(Objects::nonNull).distinct().toList());
        return moves.stream().map(m -> mapper.toResponse(m, m.getByUserId() == null ? null : names.get(m.getByUserId()))).toList();
    }

    // =====================================================================================
    // Move
    // =====================================================================================

    @Transactional
    public PipelineCardResponse move(UUID id, ApplicationMoveRequest request, AuthenticatedUser actor) {
        Application application = find(id);
        requireVersion(application, request.version(), "version");
        if (!application.inProcess()) {
            throw new BusinessException("application.restore-first");
        }
        Map<UUID, Integer> sharedPerJob = new LinkedHashMap<>();
        moveOne(application, request.toStage(), request.note(), Boolean.TRUE.equals(request.runAutoAction()), request.templateId(), actor.id(), sharedPerJob);
        repository.flush();
        publishShared(sharedPerJob);
        return card(application, false);
    }

    /**
     * Moves several applications (each with the version read; any stale one → 409 and nothing moves). Rejected or
     * withdrawn applications and those already in the stage are skipped.
     */
    @Transactional
    public BulkMoveResponse bulkMove(ApplicationBulkMoveRequest request, AuthenticatedUser actor) {
        Map<UUID, Long> versions = new LinkedHashMap<>();
        request.items().forEach(item -> versions.putIfAbsent(item.id(), item.version()));
        checkBulkSize(versions.size(), "items");
        List<Application> applications = load(versions.keySet());
        for (Application application : applications) {
            requireVersion(application, versions.get(application.getId()), "items[" + indexOf(request.items(), application.getId()) + "].version");
        }
        recordBulkAction(actor.id(), BulkAction.Kind.MOVE, applications.size());
        boolean runAutoAction = Boolean.TRUE.equals(request.runAutoAction());
        int moved = 0;
        Map<UUID, Integer> sharedPerJob = new LinkedHashMap<>();
        for (Application application : applications) {
            if (application.inProcess() && moveOne(application, request.toStage(), request.note(), runAutoAction, request.templateId(), actor.id(), sharedPerJob)) {
                moved++;
            }
        }
        repository.flush();
        publishShared(sharedPerJob);
        return new BulkMoveResponse(moved);
    }

    /**
     * @param sharedPerJob counts applications shared with the client by this move (entering the client interview with
     *     a consent that covers sharing), per job, for one notice per job
     * @return whether it moved (an application already in the stage stays as it is)
     */
    private boolean moveOne(Application application, PipelineStageKey target, String note, boolean runAutoAction, UUID templateOverride, UUID actorId,
            Map<UUID, Integer> sharedPerJob) {
        if (application.getStage() == target) {
            return false;
        }
        PipelineStageKey from = application.getStage();
        application.moveTo(target, clock.instant());
        if (target == PipelineStageKey.CLIENT_INTERVIEW && !application.isSharedWithClient() && consentCoversSharing(application)) {
            // Frontend rule: entering the client interview shares it, approved; only when the consent allows it
            application.shareWithClient(ClientDecision.APPROVED, clock.instant());
            sharedPerJob.merge(application.getJobId(), 1, Integer::sum);
        }
        stageMoves.save(new StageMove(application.getId(), StageMoveAction.MOVED, from, target, actorId, trimmedOrNull(note), clock.instant()));
        if (runAutoAction) {
            autoMessage(target, templateOverride).ifPresent(action -> messageService.sendFromTemplate(
                    action.templateId(), application.getCandidateId(), application.getId(), action.channel(), Map.of(), actorId, true));
        }
        return true;
    }

    /** The template of the automatic action: the one chosen by the recruiter, or the stage's own (if any). */
    private Optional<StageAutoActionResponse> autoMessage(PipelineStageKey target, UUID templateOverride) {
        if (templateOverride != null) {
            return Optional.of(new StageAutoActionResponse(StageAutoActionType.SEND_MESSAGE, templateOverride, null));
        }
        return pipelineStageService.list().stream()
                .filter(stage -> stage.key() == target)
                .map(PipelineStageResponse::autoAction)
                .filter(action -> action != null && action.type() == StageAutoActionType.SEND_MESSAGE && action.templateId() != null)
                .findFirst();
    }

    // =====================================================================================
    // Reject / restore
    // =====================================================================================

    /**
     * Rejects the applications in progress (the others are skipped) with an active reason; {@code notify} sends
     * the "Não seleção" email to each candidate (one email each).
     */
    @Transactional
    public RejectResponse reject(ApplicationRejectRequest request, AuthenticatedUser actor) {
        Set<UUID> ids = new LinkedHashSet<>(request.ids());
        checkBulkSize(ids.size(), "ids");
        RejectionReasonResponse reason = rejectionReasonService.list(false).stream()
                .filter(r -> r.id().equals(request.reasonId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("application.reject.reason-invalid", List.of(new FieldError("reasonId", "application.reject.reason-invalid"))));
        List<Application> applications = load(ids);
        recordBulkAction(actor.id(), BulkAction.Kind.REJECT, applications.size());
        UUID templateId = Boolean.TRUE.equals(request.notifyCandidates())
                ? messageTemplateService.findActiveByCode(REJECTION_TEMPLATE).map(MessageTemplateResponse::id)
                        .orElseThrow(() -> new BusinessException("message.template.invalid", List.of(new FieldError("notify", "message.template.invalid"))))
                : null;
        String note = trimmedOrNull(request.note());
        Instant now = clock.instant();
        int rejected = 0;
        List<UUID> closed = new ArrayList<>();
        for (Application application : applications) {
            if (application.getStatus() != ApplicationStatus.ACTIVE) {
                continue;
            }
            application.reject(reason.id(), note, actor.id(), now);
            closed.add(application.getId());
            stageMoves.save(new StageMove(application.getId(), StageMoveAction.REJECTED, application.getStage(), null, actor.id(),
                    note == null ? reason.label() : reason.label() + " – " + note, now));
            if (templateId != null) {
                messageService.sendFromTemplate(templateId, application.getCandidateId(), application.getId(), MessageChannel.EMAIL, Map.of(), actor.id(), true);
            }
            rejected++;
        }
        repository.flush();
        if (!closed.isEmpty()) {
            events.publishEvent(new ApplicationsClosedEvent(closed, actor.id()));
        }
        return new RejectResponse(rejected);
    }

    /** Back to the pipeline in the stage where it was. Only a rejected application (422 otherwise). */
    @Transactional
    public PipelineCardResponse restore(UUID id, AuthenticatedUser actor) {
        Application application = find(id);
        if (application.getStatus() != ApplicationStatus.REJECTED) {
            throw new BusinessException("application.not-rejected");
        }
        application.restore();
        stageMoves.save(new StageMove(application.getId(), StageMoveAction.RESTORED, null, application.getStage(), actor.id(), null, clock.instant()));
        repository.flush();
        return card(application, false);
    }

    // =====================================================================================
    // Edit
    // =====================================================================================

    /** Rating, tags (full list) and recruiter summary; absent fields stay. */
    @Transactional
    public PipelineCardResponse update(UUID id, ApplicationUpdateRequest request, AuthenticatedUser actor) {
        Application application = find(id);
        if (request.rating() != null) {
            application.setRating(request.rating());
        }
        if (request.tagIds() != null) {
            Set<UUID> tags = new LinkedHashSet<>(request.tagIds());
            if (!tagService.existingIds(tags).containsAll(tags)) {
                throw new BusinessException("application.tags.invalid", List.of(new FieldError("tagIds", "application.tags.invalid")));
            }
            application.getTagIds().retainAll(tags);
            application.getTagIds().addAll(tags);
        }
        if (request.recruiterSummary() != null) {
            application.setRecruiterSummary(trimmedOrNull(request.recruiterSummary()));
        }
        repository.flush();
        return card(application, false);
    }

    // =====================================================================================
    // Talent pool → job
    // =====================================================================================

    /**
     * Talent-pool candidates (active consent) into a job that accepts applications, in "Triagem" without its
     * automatic action (D8-7). Candidates already in the job are counted as duplicates and left as they are. The
     * score uses the stored profile; the application copies the profile's name, contacts, province and CV.
     */
    @Transactional
    public AddToJobResponse addToJob(AddToJobRequest request, AuthenticatedUser actor) {
        Set<UUID> candidateIds = new LinkedHashSet<>(request.candidateIds());
        checkBulkSize(candidateIds.size(), "candidateIds");
        ApplicableJob job = jobService.findApplicable(request.jobId()).orElseThrow(() -> new NotFoundException("job.not-found"));
        if (!job.publiclyVisible()) {
            throw new BusinessException("application.job-not-open", List.of(new FieldError("jobId", "application.job-not-open")));
        }
        for (UUID candidateId : candidateIds) {
            if (!consentService.hasActive(candidateId, ConsentType.TALENT_POOL)) {
                throw new BusinessException("application.talent-pool.no-consent", List.of(new FieldError("candidateIds", "application.talent-pool.no-consent")));
            }
        }
        recordBulkAction(actor.id(), BulkAction.Kind.ADD_TO_JOB, candidateIds.size());

        Instant now = clock.instant();
        String reason = messages.get("application.added-from-talent-pool");
        int added = 0;
        for (UUID candidateId : candidateIds) {
            ApplicantProfile candidate = candidateService.applicantOf(candidateId);
            // Same lock as the applications of this email: a public application at the same time cannot duplicate it
            candidateService.lockApplicantEmail(candidate.email());
            if (repository.existsByJobIdAndCandidateId(job.id(), candidateId)) {
                continue;
            }
            MatchResultResponse match = matchScorer.score(job.requirements(), candidateService.matchProfileOf(candidateId));
            Application application = new Application(job.id(), candidateId, PipelineStageKey.SCREENING, CandidateSource.TALENT_POOL, match.score(), false, now);
            application.setFullName(candidate.fullName());
            application.setEmail(candidate.email());
            application.setPhone(candidate.phone());
            application.setProvince(candidate.province());
            application.setCvDocumentId(candidate.cvDocumentId());
            application.setFromProfile(true);
            match.matches().forEach(m -> application.getMatches().add(new RequirementMatchEntry(m.requirementId(), m.met(), m.detail())));
            repository.save(application);
            stageMoves.save(new StageMove(application.getId(), StageMoveAction.APPLIED, null, PipelineStageKey.SCREENING, actor.id(), reason, now));
            added++;
        }
        repository.flush();
        return new AddToJobResponse(added, candidateIds.size() - added);
    }

    // =====================================================================================
    // Sharing with the client (stage 8c)
    // =====================================================================================

    /**
     * Shares applications in progress with the job's client company (shortlist), awaiting its decision. Each one is
     * checked against the consent text version its candidate accepted for that job: a version that does not cover
     * sharing with the client (or no job-application consent, e.g. a talent-pool candidate added by a recruiter) →
     * that application is refused with the reason and the others go on; if none could be shared → 422. Already
     * shared or not in progress → skipped. Paused and closed jobs allowed (adjustment 4). One notice per job to the
     * client's portal users, without the candidates' names.
     */
    @Transactional
    public ShareResponse share(ShareWithClientRequest request, AuthenticatedUser actor) {
        Set<UUID> ids = new LinkedHashSet<>(request.ids());
        checkBulkSize(ids.size(), "ids");
        List<Application> applications = load(ids);
        recordBulkAction(actor.id(), BulkAction.Kind.SHARE, applications.size());
        String summary = trimmedOrNull(request.summary());
        Instant now = clock.instant();
        Map<UUID, Integer> sharedPerJob = new LinkedHashMap<>();
        List<ShareResponse.Refusal> refused = new ArrayList<>();
        List<FieldError> errors = new ArrayList<>();
        int index = 0;
        for (Application application : applications) {
            int position = index++;
            if (application.getStatus() != ApplicationStatus.ACTIVE || application.isSharedWithClient()) {
                continue;
            }
            if (!consentCoversSharing(application)) {
                refused.add(new ShareResponse.Refusal(application.getId(), messages.get("application.share.no-consent")));
                errors.add(new FieldError("ids[" + position + "]", "application.share.no-consent"));
                continue;
            }
            application.shareWithClient(ClientDecision.PENDING, now);
            if (summary != null) {
                application.setRecruiterSummary(summary);
            }
            sharedPerJob.merge(application.getJobId(), 1, Integer::sum);
        }
        int shared = sharedPerJob.values().stream().mapToInt(Integer::intValue).sum();
        if (shared == 0 && !refused.isEmpty()) {
            throw new BusinessException("application.share.no-consent", errors);
        }
        repository.flush();
        publishShared(sharedPerJob);
        return new ShareResponse(shared, refused);
    }

    /**
     * A FEH OIRAM comment in the shortlist (frontend {@code addClientComment}): only for an application the client
     * sees (422 otherwise). The client's portal users are told, without the candidate's name.
     */
    @Transactional
    public PipelineCardResponse addClientComment(UUID id, ClientCommentRequest request, AuthenticatedUser actor) {
        Application application = find(id);
        if (!application.visibleToClient()) {
            throw new BusinessException("application.not-shared");
        }
        clientComments.save(new ClientComment(application.getId(), actor.id(), false, request.text().trim(), clock.instant()));
        ApplicableJob job = jobOf(application);
        events.publishEvent(new ClientCommentAddedEvent(job.id(), job.title(), job.clientId()));
        return card(application, false);
    }

    /**
     * Uploads (or replaces) the "CV para o cliente" of an application: PDF, 5 MB, checked by content, virus scan. The
     * previous one is deleted (its file after the commit).
     */
    @Transactional
    public PipelineCardResponse uploadClientCv(UUID id, String originalName, byte[] content, AuthenticatedUser actor) {
        Application application = find(id);
        UUID previous = application.getClientCvDocumentId();
        application.setClientCvDocumentId(documentService.storeClientCv(application.getCandidateId(), originalName, content, "file"));
        if (previous != null) {
            documentService.deleteClientCv(previous);
        }
        repository.flush();
        return card(application, false);
    }

    /** Removes the "CV para o cliente": the client then sees only the structured profile. */
    @Transactional
    public PipelineCardResponse removeClientCv(UUID id, AuthenticatedUser actor) {
        Application application = find(id);
        UUID previous = application.getClientCvDocumentId();
        if (previous != null) {
            application.setClientCvDocumentId(null);
            repository.flush();
            documentService.deleteClientCv(previous);
        }
        return card(application, false);
    }

    /** The consent this candidate gave for this job covers sharing with the client company (stage 8c). */
    private boolean consentCoversSharing(Application application) {
        return consentService.activeJobApplicationVersion(application.getCandidateId(), application.getJobId())
                .map(companySettings::includesClientSharing)
                .orElse(false);
    }

    private void publishShared(Map<UUID, Integer> sharedPerJob) {
        sharedPerJob.forEach((jobId, count) -> jobService.findApplicable(jobId)
                .ifPresent(job -> events.publishEvent(new CandidatesSharedEvent(job.id(), job.title(), job.clientId(), count))));
    }

    // =====================================================================================
    // Limits and cleanup
    // =====================================================================================

    private void checkBulkSize(int size, String field) {
        int max = properties.bulk().maxItems();
        if (size > max) {
            throw new BusinessException("application.bulk.too-many", List.of(new FieldError(field, "application.bulk.too-many", List.of(max))), max);
        }
    }

    /** A request with more than one item counts as a bulk action (daily limit per user). */
    private void recordBulkAction(UUID userId, BulkAction.Kind kind, int items) {
        if (items <= 1) {
            return;
        }
        Instant now = clock.instant();
        Instant windowStart = now.minus(BULK_WINDOW);
        if (bulkActions.countByUserIdAndCreatedAtAfter(userId, windowStart) >= properties.bulk().maxActionsPerDay()) {
            Duration retryAfter = bulkActions.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, windowStart)
                    .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(BULK_WINDOW)))
                    .filter(d -> !d.isNegative() && !d.isZero())
                    .orElse(Duration.ofSeconds(1));
            throw new TooManyRequestsException("application.bulk.rate-limit", retryAfter);
        }
        bulkActions.save(new BulkAction(userId, kind, items, now));
    }

    /** Daily cleanup of the bulk-action counters that left the window. */
    @Scheduled(cron = "${app.applications.cleanup-cron:0 30 3 * * *}", zone = "${app.defaults.time-zone}")
    @Transactional
    public int deleteOldBulkActions() {
        return bulkActions.deleteCreatedBefore(clock.instant().minus(BULK_WINDOW));
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Administrators and recruiters see everything; an interviewer only the jobs where they are assigned
     * (restricted view). Returns whether the view is restricted.
     */
    private static boolean checkViewer(AuthenticatedUser viewer, ApplicableJob job, String notAssignedKey) {
        if (viewer.hasRole(UserRole.ADMIN.name()) || viewer.hasRole(UserRole.RECRUITER.name())) {
            return false;
        }
        if (!viewer.hasRole(UserRole.INTERVIEWER.name())) {
            throw new ForbiddenException();
        }
        if (!job.interviewerIds().contains(viewer.id())) {
            throw new ForbiddenException(notAssignedKey);
        }
        return true;
    }

    /**
     * An application: staff of the job as {@link #checkViewer}, plus an interviewer of one of its interviews (stage 9),
     * also when not assigned to the job.
     */
    private boolean checkApplicationViewer(AuthenticatedUser viewer, Application application) {
        if (viewer.hasRole(UserRole.INTERVIEWER.name())) {
            InterviewerApplicationAccess access = interviewAccess.getIfAvailable();
            if (access != null && access.applicationIdsOf(viewer.id()).contains(application.getId())) {
                return true;
            }
        }
        return checkViewer(viewer, jobOf(application), "application.not-assigned");
    }

    /** An application to interview (stage 9): 404 when there is none. */
    @Transactional(readOnly = true)
    public InterviewTarget interviewTarget(UUID applicationId) {
        Application a = find(applicationId);
        return new InterviewTarget(a.getId(), a.getJobId(), a.getCandidateId(), a.getStatus(), a.visibleToClient());
    }

    /**
     * An application whose evaluations the viewer may read (stage 9b): 404 when missing, 403 for an interviewer neither
     * assigned to the job nor interviewer of one of its interviews.
     */
    @Transactional(readOnly = true)
    public EvaluationTarget evaluationTarget(UUID applicationId, AuthenticatedUser viewer) {
        Application a = find(applicationId);
        checkApplicationViewer(viewer, a);
        return new EvaluationTarget(a.getId(), a.getJobId(), a.getCandidateId());
    }

    /** Of these applications, the ones the viewer may read (all of them for administrators and recruiters). */
    @Transactional(readOnly = true)
    public Set<UUID> viewableApplicationIds(AuthenticatedUser viewer, Collection<UUID> applicationIds) {
        if (viewer.hasRole(UserRole.ADMIN.name()) || viewer.hasRole(UserRole.RECRUITER.name())) {
            return Set.copyOf(applicationIds);
        }
        if (!viewer.hasRole(UserRole.INTERVIEWER.name()) || applicationIds.isEmpty()) {
            return Set.of();
        }
        InterviewerApplicationAccess access = interviewAccess.getIfAvailable();
        Set<UUID> byInterview = access == null ? Set.of() : access.applicationIdsOf(viewer.id());
        Map<UUID, Boolean> assignedJobs = new HashMap<>();
        Set<UUID> result = new HashSet<>();
        for (Application application : repository.findAllById(applicationIds)) {
            boolean assigned = assignedJobs.computeIfAbsent(application.getJobId(),
                    jobId -> jobService.findApplicable(jobId).map(job -> job.interviewerIds().contains(viewer.id())).orElse(false));
            if (assigned || byInterview.contains(application.getId())) {
                result.add(application.getId());
            }
        }
        return result;
    }

    private Application find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("application.not-found"));
    }

    /** Every id must exist (404 otherwise); in the order given. */
    private List<Application> load(Collection<UUID> ids) {
        Map<UUID, Application> byId = repository.findAllById(ids).stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        if (byId.size() != new HashSet<>(ids).size()) {
            throw new NotFoundException("application.not-found");
        }
        return ids.stream().map(byId::get).toList();
    }

    private ApplicableJob jobOf(Application application) {
        return jobService.findApplicable(application.getJobId()).orElseThrow(() -> new NotFoundException("application.not-found"));
    }

    private static void requireVersion(Application application, Long version, String field) {
        if (version == null || version != application.getVersion()) {
            throw new ConflictException("error.concurrent-update", List.of(new FieldError(field, "error.concurrent-update")));
        }
    }

    private static int indexOf(List<ApplicationVersionRef> items, UUID id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private PipelineCardResponse card(Application application, boolean restricted) {
        return cards(List.of(application), restricted).getFirst();
    }

    private List<PipelineCardResponse> cards(List<Application> applications, boolean restricted) {
        Map<UUID, CandidateCard> profiles = candidateService.cardsOf(applications.stream().map(Application::getCandidateId).distinct().toList());
        Map<UUID, List<ClientCommentResponse>> comments = clientComments.findByApplicationIdInOrderByCreatedAtAscIdAsc(applications.stream().map(Application::getId).toList())
                .stream().collect(Collectors.groupingBy(ClientComment::getApplicationId, Collectors.mapping(mapper::toCommentResponse, Collectors.toList())));
        List<PipelineCardResponse> cards = new ArrayList<>(applications.size());
        for (Application application : applications) {
            CandidateCard profile = profiles.get(application.getCandidateId());
            PipelineCandidateResponse candidate = new PipelineCandidateResponse(
                    application.getCandidateId(),
                    application.getFullName(),
                    restricted || profile == null ? null : profile.avatarUrl(),
                    application.getProvince(),
                    profile == null ? "" : profile.city(),
                    profile == null ? "" : profile.headline(),
                    profile == null ? 0 : profile.totalExperienceYears(),
                    restricted ? null : Texts.blankToNull(application.getEmail()),
                    restricted ? null : Texts.blankToNull(application.getPhone()));
            cards.add(new PipelineCardResponse(ApplicationResponse.withClientComments(mapper.toResponse(application), comments.getOrDefault(application.getId(), List.of())), candidate));
        }
        return cards;
    }

    private static String trimmedOrNull(String value) {
        String text = Texts.blankToNull(value);
        return text == null ? null : text.trim();
    }
}
