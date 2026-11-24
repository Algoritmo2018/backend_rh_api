package com.minhaempresa.recrutamentoapi.candidate.controller;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.ReferenceCreateRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.ReferenceStatusRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ReferenceResponse;
import com.minhaempresa.recrutamentoapi.candidate.service.ReferenceService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Professional references (candidate page, tab "Referências"): administrators and recruiters. */
@RestController
@RequestMapping("/api/v1/references")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class ReferenceController {

    private final ReferenceService referenceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReferenceResponse create(@Valid @RequestBody ReferenceCreateRequest request) {
        return referenceService.create(request);
    }

    @PatchMapping("/{id}/status")
    public ReferenceResponse setStatus(@PathVariable UUID id, @Valid @RequestBody ReferenceStatusRequest request, AuthenticatedUser actor) {
        return referenceService.setStatus(id, request, actor);
    }
}
