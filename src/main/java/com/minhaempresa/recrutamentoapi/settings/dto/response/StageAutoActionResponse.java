package com.minhaempresa.recrutamentoapi.settings.dto.response;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.settings.entity.StageAutoActionType;
import java.util.UUID;

/** Frontend {@code StageAutoAction}: {@code templateId}/{@code channel} only for {@code send_message}. */
public record StageAutoActionResponse(StageAutoActionType type, UUID templateId, MessageChannel channel) {}
