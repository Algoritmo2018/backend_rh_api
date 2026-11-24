package com.minhaempresa.recrutamentoapi.search.service;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.search.dto.response.GlobalSearchResponse;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search of the top bar (frontend {@code searchService.global}). Interviewers only find the candidates
 * assigned to them and no jobs.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private static final int MIN_LENGTH = 2;
    private static final int LIMIT = 5;

    private final CandidateService candidateService;
    private final JobService jobService;

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String term, AuthenticatedUser viewer) {
        if (term == null || term.trim().length() < MIN_LENGTH) {
            return GlobalSearchResponse.EMPTY;
        }
        boolean interviewer = viewer.hasRole(UserRole.INTERVIEWER.name());
        return new GlobalSearchResponse(
                candidateService.quickSearch(term, viewer),
                interviewer ? List.of() : jobService.quickSearch(term, LIMIT));
    }
}
