package com.minhaempresa.recrutamentoapi.notification.event;

import java.util.UUID;

/**
 * An outbox email that belongs to a record ({@code referenceId}) was delivered or definitely failed.
 * Published inside the transaction of the outbox worker, so listeners update their record atomically with
 * the outbox row.
 *
 * @param error short reason of the failure (never the content nor a password); {@code null} when sent
 */
public record OutboundEmailStatusEvent(UUID referenceId, boolean sent, String error) {}
