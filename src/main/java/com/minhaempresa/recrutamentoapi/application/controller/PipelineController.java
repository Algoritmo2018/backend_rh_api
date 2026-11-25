package com.minhaempresa.recrutamentoapi.application.controller;

import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationBulkMoveRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationMoveRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationRejectRequest;
import com.minhaempresa.recrutamentoapi.application.dto.request.ApplicationUpdateRequest;
import com.minhaempresa.recrutamentoapi.application.dto.response.BulkMoveResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.PipelineCardResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.RejectResponse;
import com.minhaempresa.recrutamentoapi.application.dto.response.StageMoveResponse;
import com.minhaempresa.recrutamentoapi.application.service.PipelineService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pipeline of the jobs (stage 8b). Administrators and recruiters act; interviewers only read the applications of
 * the jobs where they are assigned (restricted view).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class PipelineController {

    private final PipelineService pipelineService;

    /** Frontend {@code PipelineCard[]}: best score first (no score at the end), rejected ones included. */
    @GetMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public List<PipelineCardResponse> listByJob(@PathVariable UUID jobId, AuthenticatedUser viewer) {
        return pipelineService.listByJob(jobId, viewer);
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public PipelineCardResponse get(@PathVariable UUID id, AuthenticatedUser viewer) {
        return pipelineService.get(id, viewer);
    }

    /** Frontend {@code StageMoveWithUser[]}, the newest first. */
    @GetMapping("/applications/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public List<StageMoveResponse> history(@PathVariable UUID id, AuthenticatedUser viewer) {
        return pipelineService.history(id, viewer);
    }

    /** Stale {@code version} → 409; rejected/withdrawn → 422. */
    @PostMapping("/applications/{id}/move")
    public PipelineCardResponse move(@PathVariable UUID id, @Valid @RequestBody ApplicationMoveRequest request, AuthenticatedUser actor) {
        return pipelineService.move(id, request, actor);
    }

    @PostMapping("/applications/bulk-move")
    public BulkMoveResponse bulkMove(@Valid @RequestBody ApplicationBulkMoveRequest request, AuthenticatedUser actor) {
        return pipelineService.bulkMove(request, actor);
    }

    @PostMapping("/applications/reject")
    public RejectResponse reject(@Valid @RequestBody ApplicationRejectRequest request, AuthenticatedUser actor) {
        return pipelineService.reject(request, actor);
    }

    @PostMapping("/applications/{id}/restore")
    public PipelineCardResponse restore(@PathVariable UUID id, AuthenticatedUser actor) {
        return pipelineService.restore(id, actor);
    }

    @PatchMapping("/applications/{id}")
    public PipelineCardResponse update(@PathVariable UUID id, @Valid @RequestBody ApplicationUpdateRequest request, AuthenticatedUser actor) {
        return pipelineService.update(id, request, actor);
    }
}
