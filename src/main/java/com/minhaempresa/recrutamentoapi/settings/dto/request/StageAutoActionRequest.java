package com.minhaempresa.recrutamentoapi.settings.dto.request;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.settings.entity.StageAutoActionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code channel} is optional; when sent it must be the channel of the template. */
public record StageAutoActionRequest(@NotNull(message = "{validation.required}") StageAutoActionType type, UUID templateId, MessageChannel channel) {}
