package com.minhaempresa.recrutamentoapi.job.controller;

import com.minhaempresa.recrutamentoapi.job.dto.request.JobCreateRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobFilterRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobStatusRequest;
import com.minhaempresa.recrutamentoapi.job.dto.request.JobUpdateRequest;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobListItemResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobResponse;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Jobs of the internal panel (Vagas): administrators and recruiters only. */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class JobController {

    private final JobService jobService;

    /**
     * {@code JobListItem[]} (the frontend paginates), or {@code PageResponse<JobListItem>} when
     * {@code page} or {@code pageSize} is sent.
     */
    @GetMapping
    public Object list(@Valid JobFilterRequest filter, @Valid PageQuery pageQuery) {
        if (pageQuery.page() == null && pageQuery.pageSize() == null) {
            return jobService.list(filter);
        }
        return jobService.page(filter, pageQuery);
    }

    @GetMapping("/{id}")
    public JobListItemResponse get(@PathVariable UUID id) {
        return jobService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody JobCreateRequest request, AuthenticatedUser actor) {
        return jobService.create(request, actor);
    }

    @PutMapping("/{id}")
    public JobResponse update(@PathVariable UUID id, @Valid @RequestBody JobUpdateRequest request, AuthenticatedUser actor) {
        return jobService.update(id, request, actor);
    }

    @PatchMapping("/{id}/status")
    public JobResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody JobStatusRequest request) {
        return jobService.changeStatus(id, request);
    }
}
