package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Implemented by the applications module (stage 8) so the job list can show candidates per pipeline
 * stage without the jobs module reading its tables. Until then there is no implementation and every
 * count is 0.
 */
public interface JobPipelineStatsContributor {

    /** Counts per job id (jobs without applications may be absent). */
    Map<UUID, JobPipelineCounts> countsByJob(Collection<UUID> jobIds);

    /**
     * @param stageCounts active and hired applications per stage (missing stages count 0)
     * @param totalCandidates every application of the job
     * @param activeCandidates applications still in progress
     * @param rejectedCount rejected or withdrawn applications
     */
    record JobPipelineCounts(Map<PipelineStageKey, Long> stageCounts, long totalCandidates, long activeCandidates, long rejectedCount) {}
}
