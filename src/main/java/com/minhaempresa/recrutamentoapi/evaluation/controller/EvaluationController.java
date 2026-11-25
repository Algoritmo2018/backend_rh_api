package com.minhaempresa.recrutamentoapi.evaluation.controller;

import com.minhaempresa.recrutamentoapi.evaluation.dto.request.EvaluationReopenRequest;
import com.minhaempresa.recrutamentoapi.evaluation.dto.request.EvaluationSubmitRequest;
import com.minhaempresa.recrutamentoapi.evaluation.dto.response.EvaluationResponse;
import com.minhaempresa.recrutamentoapi.evaluation.entity.EvaluationStatus;
import com.minhaempresa.recrutamentoapi.evaluation.service.EvaluationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evaluations (scorecards, stage 9b). Every staff role reads (interviewers blind, D9-7); the evaluator submits;
 * administrators and recruiters add free evaluations; administrators reopen (A4).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
public class EvaluationController {

    private final EvaluationService evaluationService;

    /** Frontend {@code getByApplication}: newest first. */
    @GetMapping("/applications/{applicationId}/evaluations")
    public List<EvaluationResponse> listByApplication(@PathVariable UUID applicationId, AuthenticatedUser viewer) {
        return evaluationService.listByApplication(applicationId, viewer);
    }

    /** Frontend {@code getByCandidate}. */
    @GetMapping("/candidates/{candidateId}/evaluations")
    public List<EvaluationResponse> listByCandidate(@PathVariable UUID candidateId, AuthenticatedUser viewer) {
        return evaluationService.listByCandidate(candidateId, viewer);
    }

    /** Frontend {@code getForEvaluator}: "Minhas avaliações" (the viewer's own, optionally {@code ?status=pending}). */
    @GetMapping("/evaluations/mine")
    public List<EvaluationResponse> mine(@RequestParam(required = false) EvaluationStatus status, AuthenticatedUser viewer) {
        return evaluationService.mine(status, viewer);
    }

    @GetMapping("/evaluations/{id}")
    public EvaluationResponse get(@PathVariable UUID id, AuthenticatedUser viewer) {
        return evaluationService.get(id, viewer);
    }

    /** The evaluator fills a pending evaluation (final once submitted). */
    @PostMapping("/evaluations/{id}/submit")
    public EvaluationResponse submit(@PathVariable UUID id, @Valid @RequestBody EvaluationSubmitRequest request, AuthenticatedUser actor) {
        return evaluationService.submit(id, request, actor);
    }

    /** Free evaluation (no interview), submitted at once. */
    @PostMapping("/applications/{applicationId}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public EvaluationResponse create(@PathVariable UUID applicationId, @Valid @RequestBody EvaluationSubmitRequest request, AuthenticatedUser actor) {
        return evaluationService.createFree(applicationId, request, actor);
    }

    /** A4: back to the evaluator, with the reason in the history. */
    @PostMapping("/evaluations/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public EvaluationResponse reopen(@PathVariable UUID id, @Valid @RequestBody EvaluationReopenRequest request, AuthenticatedUser actor) {
        return evaluationService.reopen(id, request, actor);
    }
}
