package com.minhaempresa.recrutamentoapi.evaluation.service;

import com.minhaempresa.recrutamentoapi.application.service.EvaluationTarget;
import com.minhaempresa.recrutamentoapi.application.service.PipelineService;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateContact;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.evaluation.dto.request.CompetencyScoreRequest;
import com.minhaempresa.recrutamentoapi.evaluation.dto.request.EvaluationReopenRequest;
import com.minhaempresa.recrutamentoapi.evaluation.dto.request.EvaluationSubmitRequest;
import com.minhaempresa.recrutamentoapi.evaluation.dto.response.EvaluationResponse;
import com.minhaempresa.recrutamentoapi.evaluation.entity.CompetencyScore;
import com.minhaempresa.recrutamentoapi.evaluation.entity.Evaluation;
import com.minhaempresa.recrutamentoapi.evaluation.entity.EvaluationReopening;
import com.minhaempresa.recrutamentoapi.evaluation.entity.EvaluationStatus;
import com.minhaempresa.recrutamentoapi.evaluation.mapper.EvaluationMapper;
import com.minhaempresa.recrutamentoapi.evaluation.repository.EvaluationReopeningRepository;
import com.minhaempresa.recrutamentoapi.evaluation.repository.EvaluationRepository;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewCompletedEvent;
import com.minhaempresa.recrutamentoapi.interview.service.InterviewService;
import com.minhaempresa.recrutamentoapi.interview.service.InterviewSummary;
import com.minhaempresa.recrutamentoapi.job.service.ApplicableJob;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import com.minhaempresa.recrutamentoapi.notification.service.NotificationService;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.shared.web.FrontendLinks;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserReference;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluations (scorecards) of applications (stage 9b).
 *
 * <ul>
 *   <li><b>Pending:</b> an interview marked {@code done} gives each of its interviewers a pending evaluation and a bell
 *       "Avaliação por preencher".
 *   <li><b>Submit:</b> only the evaluator; competencies scored 0–5, 0 = not evaluated (left out of the average), at
 *       least one above 0; recommendation required. Submitted = final (D9-6); the job's recruiter gets a bell.
 *   <li><b>Free evaluation:</b> administrators and recruiters evaluate an application without an interview (submitted
 *       at once).
 *   <li><b>Visibility</b> (D9-7): administrators and recruiters see every evaluation; an interviewer sees their own and,
 *       only after submitting their own for that application, the submitted ones of the others (blind). Client and
 *       candidate never see evaluations.
 *   <li><b>Reopen</b> (A4): administrators only, with a reason kept in a history table; the evaluator gets a bell.
 *   <li><b>Erasure</b> (D9-11): scores and recommendation stay; strengths, concerns, notes, comments and reopening
 *       reasons are deleted; pending evaluations never filled are removed.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRepository repository;
    private final EvaluationReopeningRepository reopenings;
    private final EvaluationMapper mapper;
    private final PipelineService pipelineService;
    private final InterviewService interviewService;
    private final CandidateService candidateService;
    private final JobService jobService;
    private final UserService userService;
    private final NotificationService notifications;
    private final FrontendLinks links;
    private final Messages messages;
    private final Clock clock;

    // =====================================================================================
    // Reads
    // =====================================================================================

    /** Evaluations of an application, newest first, as the viewer may see them (D9-7). */
    @Transactional(readOnly = true)
    public List<EvaluationResponse> listByApplication(UUID applicationId, AuthenticatedUser viewer) {
        pipelineService.evaluationTarget(applicationId, viewer);
        return responses(visible(repository.findByApplicationIdOrderByCreatedAtDesc(applicationId), viewer), viewer);
    }

    /** Evaluations of a candidate: for an interviewer, only the applications they may read, and blind (D9-7). */
    @Transactional(readOnly = true)
    public List<EvaluationResponse> listByCandidate(UUID candidateId, AuthenticatedUser viewer) {
        List<Evaluation> all = repository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Set<UUID> readable = pipelineService.viewableApplicationIds(viewer, all.stream().map(Evaluation::getApplicationId).distinct().toList());
        return responses(visible(all.stream().filter(e -> readable.contains(e.getApplicationId())).toList(), viewer), viewer);
    }

    @Transactional(readOnly = true)
    public EvaluationResponse get(UUID id, AuthenticatedUser viewer) {
        Evaluation evaluation = find(id);
        if (!isManager(viewer) && !evaluation.getEvaluatorId().equals(viewer.id())) {
            pipelineService.evaluationTarget(evaluation.getApplicationId(), viewer);
            if (!visible(repository.findByApplicationIdOrderByCreatedAtDesc(evaluation.getApplicationId()), viewer).contains(evaluation)) {
                throw new ForbiddenException("evaluation.not-visible");
            }
        }
        return responses(List.of(evaluation), viewer).getFirst();
    }

    /** "Minhas avaliações": the viewer's own evaluations, newest first, optionally by status. */
    @Transactional(readOnly = true)
    public List<EvaluationResponse> mine(EvaluationStatus status, AuthenticatedUser viewer) {
        List<Evaluation> own = status == null
                ? repository.findByEvaluatorIdOrderByCreatedAtDesc(viewer.id())
                : repository.findByEvaluatorIdAndStatusOrderByCreatedAtDesc(viewer.id(), status);
        return responses(own, viewer);
    }

    // =====================================================================================
    // Writes
    // =====================================================================================

    /** An interview was done: a pending evaluation (and a bell) for each of its interviewers, once. */
    @Transactional
    public void createPending(InterviewCompletedEvent event) {
        Set<UUID> existing = Set.copyOf(repository.findEvaluatorIdsByInterviewId(event.interviewId()));
        List<UUID> evaluators = event.interviewerIds().stream().filter(id -> !existing.contains(id)).toList();
        if (evaluators.isEmpty()) {
            return;
        }
        evaluators.forEach(evaluatorId -> repository.save(
                Evaluation.pending(event.applicationId(), event.candidateId(), event.jobId(), event.interviewId(), evaluatorId)));
        repository.flush();
        String candidate = candidateName(event.candidateId());
        String jobTitle = jobService.findApplicable(event.jobId()).map(ApplicableJob::title).orElse("—");
        Map<UUID, UserReference> users = userService.referencesOf(evaluators);
        for (UUID evaluatorId : evaluators) {
            UserReference user = users.get(evaluatorId);
            if (user != null && user.active()) {
                notifications.notify(List.of(evaluatorId), NotificationType.EVALUATION_REQUESTED, messages.get("evaluation.notice.requested.title"),
                        messages.get("evaluation.notice.requested.body", candidate, jobTitle), linkFor(user, event.candidateId(), event.applicationId()));
            }
        }
    }

    /** The evaluator fills a pending evaluation; final once submitted (D9-6). */
    @Transactional
    public EvaluationResponse submit(UUID id, EvaluationSubmitRequest request, AuthenticatedUser actor) {
        Evaluation evaluation = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("evaluation.not-found"));
        if (!evaluation.getEvaluatorId().equals(actor.id())) {
            throw new ForbiddenException("evaluation.not-evaluator");
        }
        if (evaluation.isSubmitted()) {
            throw new BusinessException("evaluation.already-submitted");
        }
        fill(evaluation, request);
        repository.flush();
        notifySubmitted(evaluation, actor.id());
        return responses(List.of(evaluation), actor).getFirst();
    }

    /** Free evaluation of an application by an administrator or recruiter (no interview), submitted at once. */
    @Transactional
    public EvaluationResponse createFree(UUID applicationId, EvaluationSubmitRequest request, AuthenticatedUser actor) {
        EvaluationTarget target = pipelineService.evaluationTarget(applicationId, actor);
        Evaluation evaluation = Evaluation.free(target.applicationId(), target.candidateId(), target.jobId(), actor.id());
        fill(evaluation, request);
        repository.saveAndFlush(evaluation);
        notifySubmitted(evaluation, actor.id());
        return responses(List.of(evaluation), actor).getFirst();
    }

    /** A4: an administrator sends a submitted evaluation back to its evaluator, with a reason kept in the history. */
    @Transactional
    public EvaluationResponse reopen(UUID id, EvaluationReopenRequest request, AuthenticatedUser actor) {
        Evaluation evaluation = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("evaluation.not-found"));
        if (!evaluation.isSubmitted()) {
            throw new BusinessException("evaluation.not-submitted");
        }
        String reason = request.reason().trim();
        evaluation.reopen();
        reopenings.save(new EvaluationReopening(evaluation.getId(), actor.id(), reason, clock.instant()));
        repository.flush();
        UserReference evaluator = userService.referencesOf(List.of(evaluation.getEvaluatorId())).get(evaluation.getEvaluatorId());
        if (evaluator != null && evaluator.active() && !evaluator.id().equals(actor.id())) {
            String jobTitle = jobService.findApplicable(evaluation.getJobId()).map(ApplicableJob::title).orElse("—");
            notifications.notify(List.of(evaluator.id()), NotificationType.EVALUATION_REOPENED, messages.get("evaluation.notice.reopened.title"),
                    messages.get("evaluation.notice.reopened.body", candidateName(evaluation.getCandidateId()), jobTitle, reason),
                    linkFor(evaluator, evaluation.getCandidateId(), evaluation.getApplicationId()));
        }
        return responses(List.of(evaluation), actor).getFirst();
    }

    /**
     * Erasure of a candidate (D9-11): scores and recommendations stay for statistics, every free text goes (strengths,
     * concerns, notes, competency comments, reopening reasons); pending evaluations never filled are deleted.
     */
    @Transactional
    public void eraseCandidate(UUID candidateId) {
        List<Evaluation> evaluations = repository.findByCandidateId(candidateId);
        if (evaluations.isEmpty()) {
            return;
        }
        reopenings.findByEvaluationIdInOrderByReopenedAtDescIdDesc(evaluations.stream().map(Evaluation::getId).toList())
                .forEach(EvaluationReopening::eraseReason);
        for (Evaluation evaluation : evaluations) {
            if (!evaluation.isSubmitted() && evaluation.getScores().isEmpty()) {
                repository.delete(evaluation);
            } else {
                evaluation.eraseTexts();
            }
        }
        repository.flush();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private void fill(Evaluation evaluation, EvaluationSubmitRequest request) {
        if (request.scores().stream().noneMatch(s -> s.score() > 0)) {
            throw new BusinessException("evaluation.scores.none", List.of(new FieldError("scores", "evaluation.scores.none")));
        }
        List<CompetencyScore> lines = new ArrayList<>(request.scores().size());
        for (CompetencyScoreRequest s : request.scores()) {
            lines.add(new CompetencyScore(s.competency().trim(), s.score(), trimmedOrNull(s.comment())));
        }
        evaluation.submit(lines, request.recommendation(), trimmedOrNull(request.strengths()), trimmedOrNull(request.concerns()), trimmedOrNull(request.notes()),
                clock.instant());
    }

    /** Bell to the job's recruiter (not when they evaluated themselves). */
    private void notifySubmitted(Evaluation evaluation, UUID evaluatorId) {
        jobService.findApplicable(evaluation.getJobId()).ifPresent(job -> {
            if (job.recruiterId() == null || job.recruiterId().equals(evaluatorId)) {
                return;
            }
            String evaluator = userService.namesOf(List.of(evaluatorId)).getOrDefault(evaluatorId, messages.get("evaluation.notice.someone"));
            notifications.notify(List.of(job.recruiterId()), NotificationType.EVALUATION_SUBMITTED, messages.get("evaluation.notice.submitted.title"),
                    messages.get("evaluation.notice.submitted.body", evaluator, candidateName(evaluation.getCandidateId()), job.title()),
                    links.inAppPath(links.candidateApplication(evaluation.getCandidateId(), evaluation.getApplicationId())));
        });
    }

    /** Interviewers fill their evaluations in "Minhas avaliações"; administrators and recruiters on the candidate page. */
    private String linkFor(UserReference user, UUID candidateId, UUID applicationId) {
        String link = user.role() == UserRole.INTERVIEWER ? links.myEvaluations() : links.candidateApplication(candidateId, applicationId);
        return links.inAppPath(link);
    }

    /**
     * D9-7: administrators and recruiters see everything; anyone else sees their own evaluations and, for the
     * applications where they already submitted their own, the submitted evaluations of the others.
     */
    private static List<Evaluation> visible(List<Evaluation> evaluations, AuthenticatedUser viewer) {
        if (isManager(viewer)) {
            return evaluations;
        }
        Set<UUID> unlocked = evaluations.stream()
                .filter(e -> e.getEvaluatorId().equals(viewer.id()) && e.isSubmitted())
                .map(Evaluation::getApplicationId)
                .collect(Collectors.toSet());
        return evaluations.stream()
                .filter(e -> e.getEvaluatorId().equals(viewer.id()) || (e.isSubmitted() && unlocked.contains(e.getApplicationId())))
                .toList();
    }

    private List<EvaluationResponse> responses(List<Evaluation> evaluations, AuthenticatedUser viewer) {
        if (evaluations.isEmpty()) {
            return List.of();
        }
        boolean manager = isManager(viewer);
        Map<UUID, List<EvaluationReopening>> history = manager
                ? reopenings.findByEvaluationIdInOrderByReopenedAtDescIdDesc(evaluations.stream().map(Evaluation::getId).toList()).stream()
                        .collect(Collectors.groupingBy(EvaluationReopening::getEvaluationId))
                : Map.of();
        Map<UUID, CandidateContact> candidates = candidateService.contactsOf(evaluations.stream().map(Evaluation::getCandidateId).distinct().toList());
        Map<UUID, String> jobTitles = new HashMap<>();
        evaluations.stream().map(Evaluation::getJobId).distinct()
                .forEach(jobId -> jobService.findApplicable(jobId).ifPresent(job -> jobTitles.put(jobId, job.title())));
        Set<UUID> userIds = new LinkedHashSet<>();
        evaluations.forEach(e -> userIds.add(e.getEvaluatorId()));
        history.values().stream().flatMap(List::stream).map(EvaluationReopening::getReopenedBy).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, String> names = userService.namesOf(userIds);
        Map<UUID, InterviewSummary> interviews = interviewService.summariesOf(
                evaluations.stream().map(Evaluation::getInterviewId).filter(Objects::nonNull).distinct().toList());

        return evaluations.stream().map(e -> {
            CandidateContact candidate = candidates.get(e.getCandidateId());
            InterviewSummary interview = e.getInterviewId() == null ? null : interviews.get(e.getInterviewId());
            List<EvaluationResponse.Reopening> reopened = history.getOrDefault(e.getId(), List.of()).stream()
                    .map(r -> new EvaluationResponse.Reopening(r.getReason(), r.getReopenedBy(),
                            r.getReopenedBy() == null ? null : names.get(r.getReopenedBy()), r.getReopenedAt()))
                    .toList();
            return mapper.toResponse(e, new EvaluationMapper.Refs(
                    candidate == null ? "—" : candidate.fullName(),
                    jobTitles.getOrDefault(e.getJobId(), "—"),
                    names.getOrDefault(e.getEvaluatorId(), "—"),
                    interview == null ? null : new EvaluationResponse.InterviewRef(interview.id(), interview.type(), interview.startsAt(), interview.mode()),
                    reopened));
        }).toList();
    }

    private String candidateName(UUID candidateId) {
        CandidateContact contact = candidateService.contactsOf(List.of(candidateId)).get(candidateId);
        return contact == null ? messages.get("evaluation.notice.candidate") : contact.fullName();
    }

    private Evaluation find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("evaluation.not-found"));
    }

    private static boolean isManager(AuthenticatedUser viewer) {
        return viewer.hasRole(UserRole.ADMIN.name()) || viewer.hasRole(UserRole.RECRUITER.name());
    }

    private static String trimmedOrNull(String value) {
        String text = Texts.blankToNull(value);
        return text == null ? null : text.trim();
    }
}
