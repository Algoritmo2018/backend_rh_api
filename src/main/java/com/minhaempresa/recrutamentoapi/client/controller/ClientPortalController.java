package com.minhaempresa.recrutamentoapi.client.controller;

import com.minhaempresa.recrutamentoapi.client.dto.request.JobRequestCreateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientPortalCompanyResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.JobRequestResponse;
import com.minhaempresa.recrutamentoapi.client.service.ClientService;
import com.minhaempresa.recrutamentoapi.client.service.JobRequestService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client portal ({@code /cliente}). Every endpoint is scoped to the company in the access token
 * ({@code clientId} claim); ids of another company answer 404. More endpoints (dashboard, jobs,
 * shortlist, interviews, reports) arrive in stage 13 and must keep the same rule (see the isolation
 * test, which covers every endpoint under this path).
 */
@RestController
@RequestMapping("/api/v1/client-portal")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class ClientPortalController {

    private final ClientService clientService;
    private final JobRequestService jobRequestService;

    @GetMapping("/company")
    public ClientPortalCompanyResponse company(AuthenticatedUser actor) {
        return clientService.requireActivePortalClient(actor);
    }

    @GetMapping("/job-requests")
    public List<JobRequestResponse> jobRequests(AuthenticatedUser actor) {
        return jobRequestService.listOwn(actor);
    }

    @GetMapping("/job-requests/{id}")
    public JobRequestResponse jobRequest(@PathVariable UUID id, AuthenticatedUser actor) {
        return jobRequestService.getOwn(id, actor);
    }

    @PostMapping("/job-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public JobRequestResponse createJobRequest(@Valid @RequestBody JobRequestCreateRequest request, AuthenticatedUser actor) {
        return jobRequestService.create(actor, request);
    }
}
