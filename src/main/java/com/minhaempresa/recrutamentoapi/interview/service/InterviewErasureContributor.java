package com.minhaempresa.recrutamentoapi.interview.service;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Erasure of a candidate (D9-11): interviews still to happen are cancelled (the interviewers and the client are told
 * without the name; nothing is sent to the candidate), the notes are blanked; the rows stay for statistics.
 */
@Component
@RequiredArgsConstructor
class InterviewErasureContributor implements CandidateErasureContributor {

    private final InterviewService interviewService;

    @Override
    public void eraseCandidate(UUID candidateId) {
        interviewService.eraseCandidate(candidateId);
    }
}
