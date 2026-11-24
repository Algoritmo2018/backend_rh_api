package com.minhaempresa.recrutamentoapi.candidate.service;

import java.util.UUID;

/**
 * Implemented by every module that keeps personal data of a candidate (applications, messages,
 * interviews, evaluations, offers…), so {@link CandidateErasureService} erases everything in one
 * transaction. Implementations delete or anonymize their own rows; files go through
 * {@code StorageCleanup.deleteAfterCommit}.
 */
public interface CandidateErasureContributor {

    void eraseCandidate(UUID candidateId);
}
