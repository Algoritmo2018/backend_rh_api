package com.minhaempresa.recrutamentoapi.search.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSearchHitResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobSearchHitResponse;
import java.util.List;

/** Frontend {@code GlobalSearchResult}: at most 5 candidates and 5 jobs. */
public record GlobalSearchResponse(List<CandidateSearchHitResponse> candidates, List<JobSearchHitResponse> jobs) {

    public static final GlobalSearchResponse EMPTY = new GlobalSearchResponse(List.of(), List.of());
}
