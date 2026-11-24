package com.minhaempresa.recrutamentoapi.client.event;

import java.util.UUID;

/** A client asked for a job in the client portal: the administrators are told. */
public record JobRequestCreatedEvent(UUID jobRequestId, UUID clientId, String clientName, String title) {}
