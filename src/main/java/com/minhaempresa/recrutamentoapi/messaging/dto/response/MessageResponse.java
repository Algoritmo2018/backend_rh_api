package com.minhaempresa.recrutamentoapi.messaging.dto.response;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageDirection;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Frontend {@code Message} plus {@code templateCode}: the snapshot of what was sent.
 *
 * @param sentById {@code null} = sent automatically by the system
 */
public record MessageResponse(
        UUID id,
        UUID candidateId,
        UUID applicationId,
        MessageChannel channel,
        MessageDirection direction,
        String subject,
        String body,
        MessageStatus status,
        UUID templateId,
        String templateCode,
        UUID sentById,
        Instant createdAt) {}
