package com.minhaempresa.recrutamentoapi.interview.controller;

import com.minhaempresa.recrutamentoapi.interview.dto.request.ChooseSlotRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewCreateRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewFilterRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewStatusRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.request.InterviewUpdateRequest;
import com.minhaempresa.recrutamentoapi.interview.dto.response.InterviewResponse;
import com.minhaempresa.recrutamentoapi.interview.service.InterviewService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interviews of the internal panel (stage 9a). Administrators and recruiters manage them; interviewers read their own
 * (agenda, details).
 */
@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class InterviewController {

    private final InterviewService interviewService;

    /** Frontend {@code InterviewWithRefs[]} by start time; an interviewer only gets their own. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public List<InterviewResponse> list(InterviewFilterRequest filter, AuthenticatedUser viewer) {
        return interviewService.list(filter, viewer);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public InterviewResponse get(@PathVariable UUID id, AuthenticatedUser viewer) {
        return interviewService.get(id, viewer);
    }

    /** 201; conflicts → 409 (resend with {@code ignoreConflicts: true}). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse create(@Valid @RequestBody InterviewCreateRequest request, AuthenticatedUser actor) {
        return interviewService.create(request, actor);
    }

    /** Reschedule or edit ({@code version}); the candidate is told only with {@code notifyCandidate}. */
    @PatchMapping("/{id}")
    public InterviewResponse update(@PathVariable UUID id, @Valid @RequestBody InterviewUpdateRequest request, AuthenticatedUser actor) {
        return interviewService.update(id, request, actor);
    }

    @PatchMapping("/{id}/status")
    public InterviewResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody InterviewStatusRequest request, AuthenticatedUser actor) {
        return interviewService.changeStatus(id, request, actor);
    }

    /** Staff "Fixar" one of the proposed slots (taken already → 409 "O horário já foi escolhido."). */
    @PostMapping("/{id}/choose-slot")
    public InterviewResponse chooseSlot(@PathVariable UUID id, @Valid @RequestBody ChooseSlotRequest request, AuthenticatedUser actor) {
        return interviewService.chooseSlotAsStaff(id, request, actor);
    }
}
