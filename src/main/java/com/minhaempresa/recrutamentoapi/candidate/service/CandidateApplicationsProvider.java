package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by the applications module (stage 8b) so the candidate page lists the applications without this
 * module depending on it (frontend {@code InternalApplication}). Interviewers only get the applications of the
 * jobs where they are assigned (D6-1).
 */
public interface CandidateApplicationsProvider {

    /** Applications of the candidate the viewer may see, the newest first. */
    List<?> applicationsOf(UUID candidateId, AuthenticatedUser viewer);
}
