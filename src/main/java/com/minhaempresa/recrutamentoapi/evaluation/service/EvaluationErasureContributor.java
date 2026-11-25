package com.minhaempresa.recrutamentoapi.evaluation.service;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Erasure of a candidate (D9-11): evaluations keep scores and recommendation, without any free text. */
@Component
@RequiredArgsConstructor
class EvaluationErasureContributor implements CandidateErasureContributor {

    private final EvaluationService evaluationService;

    @Override
    public void eraseCandidate(UUID candidateId) {
        evaluationService.eraseCandidate(candidateId);
    }
}
