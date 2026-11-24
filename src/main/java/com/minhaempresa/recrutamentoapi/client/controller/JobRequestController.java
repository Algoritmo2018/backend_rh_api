package com.minhaempresa.recrutamentoapi.client.controller;

import com.minhaempresa.recrutamentoapi.client.dto.request.JobRequestFilterRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.JobRequestReviewRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.JobRequestResponse;
import com.minhaempresa.recrutamentoapi.client.service.JobRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Job requests of every client, for FEH OIRAM staff (client detail tab "Pedidos", job conversion). */
@RestController
@RequestMapping("/api/v1/job-requests")
@RequiredArgsConstructor
public class JobRequestController {

    private final JobRequestService jobRequestService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<JobRequestResponse> list(@Valid JobRequestFilterRequest filter) {
        return jobRequestService.list(filter);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public JobRequestResponse get(@PathVariable UUID id) {
        return jobRequestService.get(id);
    }

    /** Rejects a pending request (reason shown to the client). */
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public JobRequestResponse review(@PathVariable UUID id, @Valid @RequestBody JobRequestReviewRequest request) {
        return jobRequestService.review(id, request);
    }
}
