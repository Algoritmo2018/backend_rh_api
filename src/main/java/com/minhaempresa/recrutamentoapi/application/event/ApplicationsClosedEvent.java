package com.minhaempresa.recrutamentoapi.application.event;

import java.util.List;
import java.util.UUID;

/**
 * Applications left the process (rejected; withdrawn in stage 12): their interviews still to happen are cancelled in
 * the same transaction (stage 9, D9-10).
 *
 * @param actorId the user who acted ({@code null} = the candidate or the system)
 */
public record ApplicationsClosedEvent(List<UUID> applicationIds, UUID actorId) {}
