package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.dto.response.CommentAuthorResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.InternalApplicationJobResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.InternalApplicationResponse;
import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.ClientComment;
import com.minhaempresa.recrutamentoapi.application.repository.ClientCommentRepository;
import com.minhaempresa.recrutamentoapi.application.mapper.ApplicationMapper;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateApplicationsProvider;
import com.minhaempresa.recrutamentoapi.job.service.ApplicableJob;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PipelineStageResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.RejectionReasonResponse;
import com.minhaempresa.recrutamentoapi.settings.service.PipelineStageService;
import com.minhaempresa.recrutamentoapi.settings.service.RejectionReasonService;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applications of the candidate page (frontend {@code InternalApplication}), the newest first. Interviewers only
 * get the applications of the jobs where they are assigned (D6-1, adjustment 6).
 */
@Component
@RequiredArgsConstructor
class ApplicationProfileProvider implements CandidateApplicationsProvider {

    private final ApplicationRepository repository;
    private final ClientCommentRepository clientComments;
    private final ApplicationMapper mapper;
    private final JobService jobService;
    private final UserService userService;
    private final PipelineStageService pipelineStageService;
    private final RejectionReasonService rejectionReasonService;
    private final Messages messages;

    @Override
    @Transactional(readOnly = true)
    public List<InternalApplicationResponse> applicationsOf(UUID candidateId, AuthenticatedUser viewer) {
        List<Application> applications = repository.findByCandidateIdOrderByAppliedAtDesc(candidateId);
        if (applications.isEmpty()) {
            return List.of();
        }
        Map<UUID, ApplicableJob> jobs = new HashMap<>();
        applications.stream().map(Application::getJobId).distinct()
                .forEach(jobId -> jobService.findApplicable(jobId).ifPresent(job -> jobs.put(jobId, job)));
        boolean interviewer = viewer.hasRole(UserRole.INTERVIEWER.name());
        List<Application> visible = applications.stream()
                .filter(a -> jobs.containsKey(a.getJobId()))
                .filter(a -> !interviewer || jobs.get(a.getJobId()).interviewerIds().contains(viewer.id()))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> recruiterNames = userService.namesOf(jobs.values().stream().map(ApplicableJob::recruiterId).distinct().toList());
        Map<PipelineStageKey, String> stageNames = pipelineStageService.list().stream()
                .collect(Collectors.toMap(PipelineStageResponse::key, PipelineStageResponse::name));
        Map<UUID, String> reasonLabels = rejectionReasonService.list(true).stream()
                .collect(Collectors.toMap(RejectionReasonResponse::id, RejectionReasonResponse::label));
        Map<UUID, List<ClientComment>> comments = clientComments.findByApplicationIdInOrderByCreatedAtAscIdAsc(visible.stream().map(Application::getId).toList())
                .stream().collect(Collectors.groupingBy(ClientComment::getApplicationId));
        Map<UUID, String> authorNames = userService.namesOf(comments.values().stream().flatMap(List::stream)
                .map(ClientComment::getAuthorId).filter(Objects::nonNull).distinct().toList());
        return visible.stream().map(application -> {
            ApplicableJob job = jobs.get(application.getJobId());
            InternalApplicationJobResponse jobResponse = new InternalApplicationJobResponse(job.id(), job.title(), job.status(), job.confidential(),
                    job.requirements(), job.questions(), null, job.clientId(), job.recruiterId(), job.interviewerIds(),
                    job.clientName(), recruiterNames.get(job.recruiterId()));
            List<ClientComment> own = comments.getOrDefault(application.getId(), List.of());
            Map<UUID, CommentAuthorResponse> authors = new LinkedHashMap<>();
            own.stream().filter(c -> c.getAuthorId() != null)
                    .forEach(c -> authors.putIfAbsent(c.getAuthorId(), new CommentAuthorResponse(authorNames.getOrDefault(c.getAuthorId(), "—"), c.isFromClient())));
            return new InternalApplicationResponse(
                    ApplicationResponse.withClientComments(mapper.toResponse(application), own.stream().map(mapper::toCommentResponse).toList()),
                    jobResponse,
                    stageNames.getOrDefault(application.getStage(), application.getStage().getValue()),
                    messages.get("source." + application.getSource().getValue()),
                    application.getRejectionReasonId() == null ? null : reasonLabels.get(application.getRejectionReasonId()),
                    authors);
        }).toList();
    }
}
