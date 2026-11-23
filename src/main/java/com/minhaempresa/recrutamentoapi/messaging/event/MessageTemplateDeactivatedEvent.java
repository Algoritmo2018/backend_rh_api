package com.minhaempresa.recrutamentoapi.messaging.event;

import java.util.UUID;

/**
 * A message template was deactivated ("deleted"): automatic actions that send it must be switched off
 * in the same transaction (settings module).
 */
public record MessageTemplateDeactivatedEvent(UUID templateId) {}
