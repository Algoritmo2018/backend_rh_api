package com.minhaempresa.recrutamentoapi.application.repository;

import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    List<Application> findByCandidateId(UUID candidateId);

    /** Applications of a candidate, the newest first (candidate profile). */
    List<Application> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    /** Pipeline of a job (rejected ones included): best score first, applications without a score at the end. */
    @Query("SELECT a FROM Application a WHERE a.jobId = :jobId ORDER BY a.score DESC NULLS LAST, a.appliedAt ASC, a.id ASC")
    List<Application> findPipeline(@Param("jobId") UUID jobId);

    /** Candidates (of the given ones) that already have an application to the job. */
    @Query("SELECT a.candidateId FROM Application a WHERE a.jobId = :jobId AND a.candidateId IN :candidateIds")
    List<UUID> findCandidateIdsWithApplication(@Param("jobId") UUID jobId, @Param("candidateIds") Collection<UUID> candidateIds);

    /** Tag usage (settings screen). */
    @Query("SELECT t AS tagId, COUNT(a) AS total FROM Application a JOIN a.tagIds t GROUP BY t")
    List<TagCount> countByTag();

    interface TagCount {
        UUID getTagId();

        long getTotal();
    }

    /** Applications per job, stage and status (job list counts). */
    @Query("""
            SELECT a.jobId AS jobId, a.stage AS stage, a.status AS status, COUNT(a) AS total
            FROM Application a WHERE a.jobId IN :jobIds
            GROUP BY a.jobId, a.stage, a.status""")
    List<JobStageCount> countByJobStageAndStatus(@Param("jobIds") Collection<UUID> jobIds);

    interface JobStageCount {
        UUID getJobId();

        PipelineStageKey getStage();

        ApplicationStatus getStatus();

        long getTotal();
    }

    /** Applications per candidate: all, and still in progress (talent search). */
    @Query("""
            SELECT a.candidateId AS candidateId, COUNT(a) AS total,
                   SUM(CASE WHEN a.status = com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus.ACTIVE THEN 1 ELSE 0 END) AS active
            FROM Application a WHERE a.candidateId IN :candidateIds
            GROUP BY a.candidateId""")
    List<CandidateCount> countByCandidate(@Param("candidateIds") Collection<UUID> candidateIds);

    interface CandidateCount {
        UUID getCandidateId();

        long getTotal();

        long getActive();
    }

    @Query("SELECT DISTINCT a.candidateId FROM Application a WHERE a.jobId IN :jobIds")
    List<UUID> findCandidateIdsByJobIdIn(@Param("jobIds") Collection<UUID> jobIds);

    /**
     * Claims the right to send the duplicate-application notice of a key (hash of email and job): a new key, or
     * one whose last notice is not after {@code cutoff}, is (re)stamped with {@code now} and returned; otherwise
     * nothing is returned. Atomic, so it also holds with several instances.
     */
    @Query(value = """
            INSERT INTO application_duplicate_notices (key_hash, sent_at) VALUES (:keyHash, :now)
            ON CONFLICT (key_hash) DO UPDATE SET sent_at = EXCLUDED.sent_at
            WHERE application_duplicate_notices.sent_at <= :cutoff
            RETURNING key_hash""", nativeQuery = true)
    List<String> claimDuplicateNotice(@Param("keyHash") String keyHash, @Param("now") Instant now, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = "DELETE FROM application_duplicate_notices WHERE sent_at < :before", nativeQuery = true)
    int deleteDuplicateNoticesBefore(@Param("before") Instant before);
}
