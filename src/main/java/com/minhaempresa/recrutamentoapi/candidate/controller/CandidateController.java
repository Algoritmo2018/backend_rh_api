package com.minhaempresa.recrutamentoapi.candidate.controller;

import com.minhaempresa.recrutamentoapi.candidate.dto.request.TalentSearchQuery;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateProfileResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSummaryResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ReferenceResponse;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.DocumentService;
import com.minhaempresa.recrutamentoapi.candidate.service.ReferenceService;
import com.minhaempresa.recrutamentoapi.candidate.service.TalentSearchService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Candidates for FEH OIRAM staff: talent pool search and the candidate page. */
@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final TalentSearchService talentSearchService;
    private final DocumentService documentService;
    private final ReferenceService referenceService;

    /** Talent pool (only candidates who consented), 12 per page by default. */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public PageResponse<CandidateSummaryResponse> search(@Valid TalentSearchQuery query) {
        return talentSearchService.search(query);
    }

    /** Skill suggestions (talent filters and the candidate's own skills form). */
    @GetMapping("/skills")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'CANDIDATE')")
    public List<String> skills() {
        return candidateService.skillSuggestions();
    }

    /** Every document of the candidate, sensitive ones included (never interviewers). */
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<DocumentResponse> documents(@PathVariable UUID id) {
        return documentService.listForCandidate(id);
    }

    @GetMapping("/{id}/references")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<ReferenceResponse> references(@PathVariable UUID id) {
        return referenceService.listForCandidate(id);
    }

    /** Interviewers: only candidates assigned to them, without private data. */
    @GetMapping("/{id}/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public CandidateProfileResponse profile(@PathVariable UUID id, AuthenticatedUser viewer) {
        return candidateService.profile(id, viewer);
    }
}
