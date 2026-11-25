package com.minhaempresa.recrutamentoapi.application.service;

import java.util.Set;
import java.util.UUID;

/**
 * Implemented by the interviews module (stage 9) so an interviewer of an interview reads that application (restricted
 * view) even when not assigned to the job, without this module depending on it.
 */
public interface InterviewerApplicationAccess {

    /** Applications of the (not cancelled) interviews of this interviewer. */
    Set<UUID> applicationIdsOf(UUID interviewerId);
}
