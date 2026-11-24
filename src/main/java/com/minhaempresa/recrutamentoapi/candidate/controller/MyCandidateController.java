package com.minhaempresa.recrutamentoapi.candidate.controller;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.CandidateUpdateRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.ConsentRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.request.DocumentUploadRequest;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ConsentResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.RecommendedJobResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.ConsentType;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.ConsentService;
import com.minhaempresa.recrutamentoapi.candidate.service.DocumentService;
import com.minhaempresa.recrutamentoapi.candidate.service.RecommendedJobService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The candidate's own data (candidate portal). The candidate always comes from the access token: there
 * is no candidate id in these paths.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CANDIDATE')")
public class MyCandidateController {

    private final CandidateService candidateService;
    private final ConsentService consentService;
    private final RecommendedJobService recommendedJobService;
    private final DocumentService documentService;

    /** 409 while the email is not confirmed (the profile is only created after the confirmation). */
    @GetMapping("/candidate")
    public CandidateResponse get(AuthenticatedUser actor) {
        return candidateService.getMine(actor);
    }

    @PatchMapping("/candidate")
    public CandidateResponse update(@Valid @RequestBody CandidateUpdateRequest request, AuthenticatedUser actor) {
        return candidateService.updateMine(actor, request);
    }

    @GetMapping("/consents")
    public List<ConsentResponse> consents(AuthenticatedUser actor) {
        return consentService.listMine(actor);
    }

    /** {@code talent_pool} or {@code marketing}; the job-application consent is given when applying. */
    @PutMapping("/consents/{type}")
    public List<ConsentResponse> setConsent(@PathVariable ConsentType type, @Valid @RequestBody ConsentRequest request, AuthenticatedUser actor) {
        return consentService.setMine(actor, type, request.granted());
    }

    @GetMapping("/documents")
    public List<DocumentResponse> documents(AuthenticatedUser actor) {
        return documentService.listMine(actor);
    }

    /**
     * Multipart: part {@code file} plus {@code type}, {@code reference} (BI/NIF/INSS) and
     * {@code expiresAt}. The format is detected from the content; limits: CV 5 MB, photo 2 MB (PNG/JPG),
     * other documents 10 MB.
     */
    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(@RequestPart("file") MultipartFile file, @Valid DocumentUploadRequest request, AuthenticatedUser actor)
            throws IOException {
        return documentService.uploadMine(actor, request, file.getOriginalFilename(), file.getBytes());
    }

    /** An approved document cannot be deleted (422). */
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID id, AuthenticatedUser actor) {
        documentService.deleteMine(actor, id);
    }

    @GetMapping("/recommended-jobs")
    public List<RecommendedJobResponse> recommendedJobs(
            @RequestParam(defaultValue = "3")
            @Min(value = 1, message = "{candidate.recommended-limit.range}") @Max(value = 10, message = "{candidate.recommended-limit.range}") int limit,
            AuthenticatedUser actor) {
        return recommendedJobService.recommendedForMe(actor, limit);
    }
}
