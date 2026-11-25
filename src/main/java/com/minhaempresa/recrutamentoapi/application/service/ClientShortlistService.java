package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.dto.response.ClientCommentView;
import com.minhaempresa.recrutamentoapi.application.dto.response.ClientSharedApplicationResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.ClientSharedCandidateResponse;
import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.ClientComment;
import com.minhaempresa.recrutamentoapi.application.entity.QuickProfile;
import com.minhaempresa.recrutamentoapi.application.mapper.ApplicationMapper;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.application.repository.ClientCommentRepository;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentFile;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.DocumentService;
import com.minhaempresa.recrutamentoapi.candidate.service.SharedProfile;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientPortalCompanyResponse;
import com.minhaempresa.recrutamentoapi.client.service.ClientService;
import com.minhaempresa.recrutamentoapi.job.service.ApplicableJob;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PipelineStageResponse;
import com.minhaempresa.recrutamentoapi.settings.service.PipelineStageService;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared candidates in the client portal (stage 8c, read only; decisions and client comments arrive in stage 13).
 *
 * <ul>
 *   <li><b>Isolation:</b> the company comes from the access token, never from the request. A job of another company,
 *       a missing id or an application the client may not see → 404 with the same body; an inactive company → 403.
 *   <li><b>Visible:</b> shared and in progress or hired. Rejected by FEH OIRAM, withdrawn or erased → gone (404).
 *   <li><b>Fields:</b> only the approved list ({@link ClientSharedCandidateResponse}). An anonymous application shows
 *       only what it sent (name, province, quick profile), never the stored profile of the email owner.
 *   <li><b>File:</b> only the "CV para o cliente" ({@code client_cv}), through the API, logged; never the original CV.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ClientShortlistService {

    private final ApplicationRepository repository;
    private final ClientCommentRepository clientComments;
    private final ApplicationMapper mapper;
    private final ClientService clientService;
    private final JobService jobService;
    private final CandidateService candidateService;
    private final DocumentService documentService;
    private final PipelineStageService pipelineStageService;
    private final UserService userService;

    /** Shared candidates of a job of the own company: best score first (no score at the end). */
    @Transactional(readOnly = true)
    public List<ClientSharedApplicationResponse> shortlist(UUID jobId, AuthenticatedUser actor) {
        ClientPortalCompanyResponse company = clientService.requireActivePortalClient(actor);
        jobService.findApplicable(jobId)
                .filter(job -> company.id().equals(job.clientId()))
                .orElseThrow(() -> new NotFoundException("job.not-found"));
        return views(repository.findPipeline(jobId).stream().filter(Application::visibleToClient).toList());
    }

    @Transactional(readOnly = true)
    public ClientSharedApplicationResponse get(UUID id, AuthenticatedUser actor) {
        return views(List.of(visible(id, actor))).getFirst();
    }

    /** The "CV para o cliente" of a visible application (404 when there is none: the client sees only the profile). */
    @Transactional
    public DocumentFile clientCv(UUID id, boolean inline, AuthenticatedUser actor) {
        Application application = visible(id, actor);
        if (application.getClientCvDocumentId() == null) {
            throw new NotFoundException("document.not-found");
        }
        return documentService.openClientCv(application.getClientCvDocumentId(), inline, actor.id());
    }

    // =====================================================================================

    /** An application the client of the token may see; anything else is "not found" (same body). */
    private Application visible(UUID id, AuthenticatedUser actor) {
        ClientPortalCompanyResponse company = clientService.requireActivePortalClient(actor);
        return repository.findById(id)
                .filter(Application::visibleToClient)
                .filter(application -> jobService.findApplicable(application.getJobId()).map(ApplicableJob::clientId).filter(company.id()::equals).isPresent())
                .orElseThrow(() -> new NotFoundException("application.not-found"));
    }

    private List<ClientSharedApplicationResponse> views(List<Application> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        Map<PipelineStageKey, String> stageNames = pipelineStageService.list().stream()
                .collect(Collectors.toMap(PipelineStageResponse::key, PipelineStageResponse::name));
        Map<UUID, List<ClientComment>> comments = clientComments.findByApplicationIdInOrderByCreatedAtAscIdAsc(applications.stream().map(Application::getId).toList())
                .stream().collect(Collectors.groupingBy(ClientComment::getApplicationId));
        Map<UUID, String> authorNames = userService.namesOf(comments.values().stream().flatMap(List::stream)
                .map(ClientComment::getAuthorId).filter(Objects::nonNull).distinct().toList());
        return applications.stream().map(application -> new ClientSharedApplicationResponse(
                application.getId(),
                application.getJobId(),
                application.getStage(),
                stageNames.getOrDefault(application.getStage(), application.getStage().getValue()),
                application.getScore(),
                application.getSharedAt(),
                application.getClientDecision(),
                application.getRecruiterSummary(),
                comments.getOrDefault(application.getId(), List.of()).stream()
                        .map(c -> new ClientCommentView(c.getId(), c.getAuthorId() == null ? null : authorNames.get(c.getAuthorId()), c.isFromClient(), c.getText(),
                                c.getCreatedAt()))
                        .toList(),
                candidate(application),
                application.getQuickProfile() == null ? null : mapper.toQuickProfileResponse(application.getQuickProfile()),
                application.getClientCvDocumentId() == null ? null : "/api/v1/client-portal/applications/" + application.getId() + "/client-cv"))
                .toList();
    }

    /**
     * The approved fields: the stored profile only for an application made with it (logged-in candidate, talent
     * pool); an anonymous application → only what it sent. Name and province: the copy sent (D8-1). Never city nor
     * photo.
     */
    private ClientSharedCandidateResponse candidate(Application application) {
        if (application.isFromProfile()) {
            SharedProfile p = candidateService.sharedProfileOf(application.getCandidateId());
            return new ClientSharedCandidateResponse(application.getFullName(), p.headline(), p.summary(), application.getProvince(), null, null,
                    p.experience(), p.education(), p.languages(), p.skills(), p.driverLicenses(), p.availability(), p.totalExperienceYears());
        }
        QuickProfile quick = application.getQuickProfile();
        if (quick == null) {
            return new ClientSharedCandidateResponse(application.getFullName(), "", null, application.getProvince(), null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), null, 0);
        }
        List<DriverLicenseCategory> licenses = quick.getDriverLicenses() == null ? List.of()
                : quick.getDriverLicenses().stream().map(DriverLicenseCategory::valueOf).toList();
        return new ClientSharedCandidateResponse(application.getFullName(), quick.getHeadline(), null, application.getProvince(), null, null,
                List.of(), List.of(), List.of(), quick.getSkills() == null ? List.of() : List.copyOf(quick.getSkills()), licenses, null,
                quick.getYears() == null ? 0 : quick.getYears());
    }
}
