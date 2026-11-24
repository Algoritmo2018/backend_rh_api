package com.minhaempresa.recrutamentoapi.client.service;

import com.minhaempresa.recrutamentoapi.client.dto.request.JobRequestCreateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.JobRequestFilterRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.JobRequestReviewRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientPortalCompanyResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.JobRequestResponse;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequest;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import com.minhaempresa.recrutamentoapi.client.event.JobRequestCreatedEvent;
import com.minhaempresa.recrutamentoapi.client.event.JobRequestReviewedEvent;
import com.minhaempresa.recrutamentoapi.client.mapper.JobRequestMapper;
import com.minhaempresa.recrutamentoapi.client.repository.JobRequestRepository;
import com.minhaempresa.recrutamentoapi.client.repository.JobRequestSpecifications;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job requests: made by clients in the client portal (always scoped to the company in the access
 * token), reviewed by FEH OIRAM. A request of another client is reported as "not found" (404), never
 * as forbidden, so ids of other companies cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class JobRequestService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final JobRequestRepository repository;
    private final JobRequestMapper mapper;
    private final ClientService clientService;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final AppProperties appProperties;
    private final Clock clock;

    // =====================================================================================
    // Staff (administrators and recruiters)
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<JobRequestResponse> list(JobRequestFilterRequest filter) {
        return toResponses(repository.findAll(
                JobRequestSpecifications.clientId(filter.clientId()).and(JobRequestSpecifications.status(filter.status())),
                NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public JobRequestResponse get(UUID id) {
        return toResponse(find(id));
    }

    /**
     * Rejects a pending request with a reason the client sees. Approving is not available (no screen
     * uses it) and conversion happens when a job is created from the request (stage 5).
     */
    @Transactional
    public JobRequestResponse review(UUID id, JobRequestReviewRequest request) {
        if (request.status() != JobRequestStatus.REJECTED) {
            throw new BusinessException("job-request.review.only-reject", List.of(new FieldError("status", "job-request.review.only-reject")));
        }
        JobRequest jobRequest = findPending(id);
        jobRequest.reject(request.note().trim(), clock.instant());
        events.publishEvent(new JobRequestReviewedEvent(jobRequest.getId(), jobRequest.getRequestedById(), jobRequest.getTitle(), JobRequestStatus.REJECTED, jobRequest.getReviewNote()));
        return toResponse(jobRequest);
    }

    /**
     * Locks a pending request that is about to become a job (first step of the conversion, in the
     * transaction that creates the job). A missing or already reviewed request → 422 on
     * {@code jobRequestId}. The lock makes a concurrent conversion of the same request wait and then
     * fail here.
     */
    @Transactional
    public JobRequestResponse lockPendingForConversion(UUID id) {
        JobRequest jobRequest = repository.findByIdForUpdate(id)
                .filter(JobRequest::isPending)
                .orElseThrow(() -> new BusinessException("job.job-request.unavailable", List.of(new FieldError("jobRequestId", "job.job-request.unavailable"))));
        return toResponse(jobRequest);
    }

    /**
     * Marks a pending request as converted into the given job. Called by the jobs module in the
     * transaction that creates the job (after {@link #lockPendingForConversion}), so both happen or
     * neither does.
     */
    @Transactional
    public JobRequestResponse markConverted(UUID id, UUID jobId) {
        JobRequest jobRequest = findPending(id);
        jobRequest.convert(Objects.requireNonNull(jobId), clock.instant());
        events.publishEvent(new JobRequestReviewedEvent(jobRequest.getId(), jobRequest.getRequestedById(), jobRequest.getTitle(), JobRequestStatus.CONVERTED, null));
        return toResponse(jobRequest);
    }

    // =====================================================================================
    // Client portal (the company always comes from the access token)
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<JobRequestResponse> listOwn(AuthenticatedUser actor) {
        ClientPortalCompanyResponse company = clientService.requireActivePortalClient(actor);
        return toResponses(repository.findByClientId(company.id(), NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public JobRequestResponse getOwn(UUID id, AuthenticatedUser actor) {
        ClientPortalCompanyResponse company = clientService.requireActivePortalClient(actor);
        return toResponse(repository.findByIdAndClientId(id, company.id()).orElseThrow(JobRequestService::notFound));
    }

    @Transactional
    public JobRequestResponse create(AuthenticatedUser actor, JobRequestCreateRequest request) {
        ClientPortalCompanyResponse company = clientService.requireActivePortalClient(actor);
        LocalDate today = LocalDate.now(clock.withZone(appProperties.defaults().timeZone()));
        if (request.desiredStartDate() != null && request.desiredStartDate().isBefore(today)) {
            throw new BusinessException("job-request.start-date.past", List.of(new FieldError("desiredStartDate", "job-request.start-date.past")));
        }
        JobRequest jobRequest = new JobRequest(company.id(), actor.id());
        jobRequest.setTitle(request.title().trim());
        jobRequest.setArea(request.area());
        jobRequest.setProvince(request.province());
        jobRequest.setVacancies(request.vacancies());
        jobRequest.setDescription(request.description().trim());
        jobRequest.setRequirements(request.requirements().trim());
        jobRequest.setSalaryBudget(request.salaryBudget());
        jobRequest.setDesiredStartDate(request.desiredStartDate());
        jobRequest.setUrgency(request.urgency());
        repository.save(jobRequest);
        events.publishEvent(new JobRequestCreatedEvent(jobRequest.getId(), company.id(), company.name(), jobRequest.getTitle()));
        return toResponse(jobRequest);
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private JobRequest find(UUID id) {
        return repository.findById(id).orElseThrow(JobRequestService::notFound);
    }

    private JobRequest findPending(UUID id) {
        JobRequest jobRequest = find(id);
        if (!jobRequest.isPending()) {
            throw new BusinessException("job-request.not-pending");
        }
        return jobRequest;
    }

    private JobRequestResponse toResponse(JobRequest jobRequest) {
        return toResponses(List.of(jobRequest)).getFirst();
    }

    private List<JobRequestResponse> toResponses(List<JobRequest> requests) {
        Map<UUID, String> clientNames = clientService.namesOf(requests.stream().map(JobRequest::getClientId).toList());
        Map<UUID, String> userNames = userService.namesOf(requests.stream().map(JobRequest::getRequestedById).filter(Objects::nonNull).toList());
        return requests.stream()
                .map(r -> mapper.toResponse(r, clientNames.get(r.getClientId()), r.getRequestedById() == null ? null : userNames.get(r.getRequestedById())))
                .toList();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("job-request.not-found");
    }
}
