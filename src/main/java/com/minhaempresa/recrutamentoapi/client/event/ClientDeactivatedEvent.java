package com.minhaempresa.recrutamentoapi.client.event;

import java.util.UUID;

/**
 * A client company was deactivated. The users module ends the sessions of its portal users in the same
 * transaction (they can no longer log in or refresh while the client is inactive).
 */
public record ClientDeactivatedEvent(UUID clientId) {}
