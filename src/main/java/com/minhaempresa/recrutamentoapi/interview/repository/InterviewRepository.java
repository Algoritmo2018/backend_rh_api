package com.minhaempresa.recrutamentoapi.interview.repository;

import com.minhaempresa.recrutamentoapi.interview.entity.Interview;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewRepository extends JpaRepository<Interview, UUID>, JpaSpecificationExecutor<Interview> {

    /** Locks the interview until the end of the transaction (choosing a slot: the first choice wins, A1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Interview i WHERE i.id = :id")
    Optional<Interview> findByIdForUpdate(@Param("id") UUID id);

    List<Interview> findByApplicationIdInAndStatusIn(Collection<UUID> applicationIds, Collection<InterviewStatus> statuses);

    List<Interview> findByJobIdAndStatusIn(UUID jobId, Collection<InterviewStatus> statuses);

    List<Interview> findByCandidateId(UUID candidateId);

    /** Candidates of the (not cancelled) interviews of an interviewer: visible to them (D6-1, stage 9). */
    @Query("""
            SELECT DISTINCT i.candidateId FROM Interview i JOIN i.interviewerIds u
            WHERE u = :userId AND i.status <> com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus.CANCELLED""")
    List<UUID> findCandidateIdsByInterviewer(@Param("userId") UUID userId);

    /** Applications of the (not cancelled) interviews of an interviewer. */
    @Query("""
            SELECT DISTINCT i.applicationId FROM Interview i JOIN i.interviewerIds u
            WHERE u = :userId AND i.status <> com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus.CANCELLED""")
    List<UUID> findApplicationIdsByInterviewer(@Param("userId") UUID userId);

    /**
     * Scheduled interviews overlapping {@code [start, end)} of this candidate or of these interviewers (other than
     * {@code exceptId}). One row per (interview, interviewer); {@code userId} null when only the candidate overlaps.
     */
    @Query(value = """
            SELECT i.id AS interviewId, i.starts_at AS startsAt, i.duration_minutes AS durationMinutes,
                   CASE WHEN i.candidate_id = :candidateId THEN TRUE ELSE FALSE END AS sameCandidate,
                   ii.user_id AS userId
            FROM interviews i
            LEFT JOIN interview_interviewers ii ON ii.interview_id = i.id AND ii.user_id IN (:userIds)
            WHERE i.status = 'SCHEDULED' AND i.id <> :exceptId
              AND i.starts_at < :end AND i.starts_at + make_interval(mins => i.duration_minutes) > :start
              AND (i.candidate_id = :candidateId OR ii.user_id IS NOT NULL)
            ORDER BY i.starts_at""", nativeQuery = true)
    List<ConflictRow> findConflicts(@Param("exceptId") UUID exceptId, @Param("candidateId") UUID candidateId, @Param("userIds") Collection<UUID> userIds,
            @Param("start") Instant start, @Param("end") Instant end);

    interface ConflictRow {
        UUID getInterviewId();

        Instant getStartsAt();

        int getDurationMinutes();

        boolean getSameCandidate();

        UUID getUserId();
    }
}
