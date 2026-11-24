package com.minhaempresa.recrutamentoapi.candidate.service;

import java.util.Set;
import java.util.UUID;

/**
 * Tells which candidates an interviewer may see (frontend {@code canViewApplication}): those with an
 * application to a job where the interviewer is assigned (stage 8) or with an interview of theirs
 * (stage 9). Until those modules implement it, interviewers see no candidate.
 */
public interface CandidateAccessContributor {

    /** Candidates this interviewer may see because of this module. */
    Set<UUID> candidatesVisibleTo(UUID interviewerId);
}
