package com.minhaempresa.recrutamentoapi.application.controller;

import com.minhaempresa.recrutamentoapi.application.dto.request.AddToJobRequest;
import com.minhaempresa.recrutamentoapi.application.dto.response.AddToJobResponse;
import com.minhaempresa.recrutamentoapi.application.service.PipelineService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Talent pool → job (stage 8b). */
@RestController
@RequestMapping("/api/v1/talent-pool")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class TalentPoolController {

    private final PipelineService pipelineService;

    /**
     * Talent-pool candidates into an open job, stage "Triagem" (D8-7): {@code {added, duplicates}}. A candidate
     * without an active talent-pool consent → 422; a job that does not accept applications → 422.
     */
    @PostMapping("/add-to-job")
    public AddToJobResponse addToJob(@Valid @RequestBody AddToJobRequest request, AuthenticatedUser actor) {
        return pipelineService.addToJob(request, actor);
    }
}
