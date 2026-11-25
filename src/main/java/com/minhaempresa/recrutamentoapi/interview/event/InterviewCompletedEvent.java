package com.minhaempresa.recrutamentoapi.interview.event;

import java.util.Set;
import java.util.UUID;

/**
 * An interview was marked {@code done} (stage 9b): each interviewer gets a pending evaluation, in the same transaction
 * (synchronous listener).
 *
 * @param actorId the administrator or recruiter who recorded the result
 */
public record InterviewCompletedEvent(UUID interviewId, UUID applicationId, UUID candidateId, UUID jobId, Set<UUID> interviewerIds, UUID actorId) {}
