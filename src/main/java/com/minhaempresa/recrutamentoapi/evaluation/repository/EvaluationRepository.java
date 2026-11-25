package com.minhaempresa.recrutamentoapi.evaluation.repository;

import com.minhaempresa.recrutamentoapi.evaluation.entity.Evaluation;
import com.minhaempresa.recrutamentoapi.evaluation.entity.EvaluationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    /** Locks the evaluation until the end of the transaction (submit / reopen: one at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evaluation e WHERE e.id = :id")
    Optional<Evaluation> findByIdForUpdate(@Param("id") UUID id);

    List<Evaluation> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<Evaluation> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

    List<Evaluation> findByEvaluatorIdOrderByCreatedAtDesc(UUID evaluatorId);

    List<Evaluation> findByEvaluatorIdAndStatusOrderByCreatedAtDesc(UUID evaluatorId, EvaluationStatus status);

    /** Evaluators who already have a scorecard for this interview (the pending ones are created once). */
    @Query("SELECT e.evaluatorId FROM Evaluation e WHERE e.interviewId = :interviewId")
    List<UUID> findEvaluatorIdsByInterviewId(@Param("interviewId") UUID interviewId);

    List<Evaluation> findByCandidateId(UUID candidateId);

    List<Evaluation> findByIdIn(Collection<UUID> ids);
}
