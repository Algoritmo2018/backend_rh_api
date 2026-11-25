package com.minhaempresa.recrutamentoapi.evaluation.repository;

import com.minhaempresa.recrutamentoapi.evaluation.entity.EvaluationReopening;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationReopeningRepository extends JpaRepository<EvaluationReopening, UUID> {

    List<EvaluationReopening> findByEvaluationIdInOrderByReopenedAtDescIdDesc(Collection<UUID> evaluationIds);
}
