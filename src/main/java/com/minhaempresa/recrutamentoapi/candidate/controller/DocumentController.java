package com.minhaempresa.recrutamentoapi.candidate.controller;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.DocumentReviewRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentFile;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentResponse;
import com.minhaempresa.recrutamentoapi.candidate.service.DocumentService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document files and their review. Files are only ever served here, after the permission check (no
 * public bucket link): fetch them with the access token.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /** Approve or reject (reason shown to the candidate). */
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public DocumentResponse review(@PathVariable UUID id, @Valid @RequestBody DocumentReviewRequest request, AuthenticatedUser reviewer) {
        return documentService.review(id, request, reviewer);
    }

    /**
     * The file. The owner, administrators and recruiters; interviewers only the CV of their candidates.
     * {@code ?inline=true} opens PDF and images in the browser; anything else is a download. The page
     * that shows it cannot run scripts ({@code CSP: sandbox}) and nothing is cached.
     */
    @GetMapping("/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER', 'CANDIDATE')")
    public ResponseEntity<Resource> file(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean inline, AuthenticatedUser viewer) {
        DocumentFile file = documentService.open(id, inline, viewer);
        ContentDisposition disposition = ContentDisposition.builder(file.inline() ? "inline" : "attachment")
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(file.content());
    }
}
