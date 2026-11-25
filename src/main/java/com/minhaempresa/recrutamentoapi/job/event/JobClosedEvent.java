package com.minhaempresa.recrutamentoapi.job.event;

import java.util.UUID;

/**
 * A job was closed (final): its interviews still to happen are cancelled in the same transaction (stage 9, D9-10).
 *
 * @param actorId the user who closed it
 */
public record JobClosedEvent(UUID jobId, UUID actorId) {}
