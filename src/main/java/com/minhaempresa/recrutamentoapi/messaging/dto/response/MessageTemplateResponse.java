package com.minhaempresa.recrutamentoapi.messaging.dto.response;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageTemplateCategory;
import java.time.Instant;
import java.util.UUID;

/** Frontend {@code MessageTemplate} plus {@code code}, {@code active} and {@code system}. */
public record MessageTemplateResponse(
        UUID id,
        String code,
        String name,
        MessageChannel channel,
        MessageTemplateCategory category,
        String subject,
        String body,
        boolean active,
        boolean system,
        Instant updatedAt) {}
