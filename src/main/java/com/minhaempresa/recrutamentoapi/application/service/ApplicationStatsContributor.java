package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository.JobStageCount;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateApplicationStatsContributor;
import com.minhaempresa.recrutamentoapi.job.service.JobPipelineStatsContributor;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts of applications for the job list (candidates per stage, total, in progress, rejected) and for the
 * talent search results (applications per candidate).
 */
@Component
@RequiredArgsConstructor
class ApplicationStatsContributor implements JobPipelineStatsContributor, CandidateApplicationStatsContributor {

    private final ApplicationRepository repository;

    /**
     * Per stage: applications in progress and hired; {@code rejectedCount}: rejected or withdrawn (frontend
     * {@code JobListItem}).
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, JobPipelineCounts> countsByJob(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<JobStageCount>> byJob = repository.countByJobStageAndStatus(jobIds).stream()
                .collect(Collectors.groupingBy(JobStageCount::getJobId));
        Map<UUID, JobPipelineCounts> result = new HashMap<>();
        byJob.forEach((jobId, rows) -> {
            Map<PipelineStageKey, Long> stages = new EnumMap<>(PipelineStageKey.class);
            long total = 0;
            long active = 0;
            long rejected = 0;
            for (JobStageCount row : rows) {
                total += row.getTotal();
                switch (row.getStatus()) {
                    case ACTIVE -> {
                        active += row.getTotal();
                        stages.merge(row.getStage(), row.getTotal(), Long::sum);
                    }
                    case HIRED -> stages.merge(row.getStage(), row.getTotal(), Long::sum);
                    case REJECTED, WITHDRAWN -> rejected += row.getTotal();
                }
            }
            result.put(jobId, new JobPipelineCounts(stages, total, active, rejected));
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ApplicationCounts> countsByCandidate(Collection<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        return repository.countByCandidate(candidateIds).stream()
                .collect(Collectors.toMap(ApplicationRepository.CandidateCount::getCandidateId, c -> new ApplicationCounts(c.getTotal(), c.getActive())));
    }
}
