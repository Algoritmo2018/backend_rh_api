package com.minhaempresa.recrutamentoapi.settings.controller;

import com.minhaempresa.recrutamentoapi.settings.dto.request.PipelineStageUpdateRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.request.RejectionReasonRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.request.TagRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PipelineStageResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.RejectionReasonResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.TagResponse;
import com.minhaempresa.recrutamentoapi.settings.service.PipelineStageService;
import com.minhaempresa.recrutamentoapi.settings.service.RejectionReasonService;
import com.minhaempresa.recrutamentoapi.settings.service.TagService;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pipeline stages, rejection reasons and tags. Every internal role reads them (Kanban, candidate
 * profile); only the administrator changes them (Configurações).
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class CatalogController {

    private static final String INTERNAL = "hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final PipelineStageService stages;
    private final RejectionReasonService reasons;
    private final TagService tags;

    // ----- Pipeline stages -----

    @GetMapping("/stages")
    @PreAuthorize(INTERNAL)
    public List<PipelineStageResponse> stages() {
        return stages.list();
    }

    /** Body: every stage (the frontend sends the whole list). */
    @PutMapping("/stages")
    @PreAuthorize(ADMIN)
    public List<PipelineStageResponse> updateStages(@RequestBody List<@Valid PipelineStageUpdateRequest> request) {
        return stages.update(request);
    }

    // ----- Rejection reasons -----

    /** {@code includeInactive=true} (settings screen) is for administrators only. */
    @GetMapping("/rejection-reasons")
    @PreAuthorize(INTERNAL)
    public List<RejectionReasonResponse> rejectionReasons(@RequestParam(defaultValue = "false") boolean includeInactive, AuthenticatedUser actor) {
        if (includeInactive && !actor.hasRole("ADMIN")) {
            throw new ForbiddenException();
        }
        return reasons.list(includeInactive);
    }

    @PostMapping("/rejection-reasons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMIN)
    public RejectionReasonResponse createRejectionReason(@Valid @RequestBody RejectionReasonRequest request) {
        return reasons.create(request);
    }

    @PutMapping("/rejection-reasons/{id}")
    @PreAuthorize(ADMIN)
    public RejectionReasonResponse updateRejectionReason(@PathVariable UUID id, @Valid @RequestBody RejectionReasonRequest request) {
        return reasons.update(id, request);
    }

    // ----- Tags -----

    @GetMapping("/tags")
    @PreAuthorize(INTERNAL)
    public List<TagResponse> tags() {
        return tags.list();
    }

    @GetMapping("/tags/usage")
    @PreAuthorize(ADMIN)
    public Map<UUID, Long> tagUsage() {
        return tags.usage();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMIN)
    public TagResponse createTag(@Valid @RequestBody TagRequest request) {
        return tags.create(request);
    }

    @PutMapping("/tags/{id}")
    @PreAuthorize(ADMIN)
    public TagResponse updateTag(@PathVariable UUID id, @Valid @RequestBody TagRequest request) {
        return tags.update(id, request);
    }

    /** Really deletes the tag and removes it from every record. */
    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ADMIN)
    public void deleteTag(@PathVariable UUID id) {
        tags.delete(id);
    }
}
