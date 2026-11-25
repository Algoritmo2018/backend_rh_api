package com.minhaempresa.recrutamentoapi.messaging.dto.response;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageDirection;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Frontend {@code Message} plus {@code templateCode}: the snapshot of what was sent.
 *
 * @param sentById the user who caused the send; {@code null} only when no user did (public application)
 * @param automatic triggered by the system (stage automatic action, bulk action), not written by hand
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
        boolean automatic,
        Instant createdAt) {}
