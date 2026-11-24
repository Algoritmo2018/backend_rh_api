package com.minhaempresa.recrutamentoapi.client.event;

import java.util.UUID;

/**
 * A client company was edited (name, status, contacts…). Modules that cache client data (public job
 * pages show the company name) react to it after the commit.
 */
public record ClientUpdatedEvent(UUID clientId) {}
