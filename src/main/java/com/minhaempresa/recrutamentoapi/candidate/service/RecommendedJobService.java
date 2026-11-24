package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.RecommendedJobResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.MatchableJobResponse;
import com.minhaempresa.recrutamentoapi.job.service.PublicJobService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jobs recommended to the candidate (portal dashboard): visible jobs ordered by the match score of the
 * own profile, computed on the fly. Jobs already applied to (stage 8) and the job-alert bonus (+15 per
 * area of an active alert) are added when those modules exist.
 */
@Service
@RequiredArgsConstructor
public class RecommendedJobService {

    private final CandidateService candidateService;
    private final PublicJobService publicJobService;

    @Transactional
    public List<RecommendedJobResponse> recommendedForMe(AuthenticatedUser actor, int limit) {
        UUID candidateId = candidateService.ownCandidateId(actor);
        List<MatchableJobResponse> jobs = publicJobService.matchableJobs();
        return jobs.stream()
                .map(job -> new RecommendedJobResponse(job.job(), candidateService.match(candidateId, job.requirements()).score()))
                .sorted(Comparator.comparingInt(RecommendedJobResponse::match).reversed())
                .limit(limit)
                .toList();
    }
}
