package com.minhaempresa.recrutamentoapi.messaging.dto.response;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageDirection;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Frontend {@code MessageWithRefs}: the message plus names for the inbox.
 *
 * @param jobTitle title of the job of the application ({@code null} until stage 8)
 * @param sentByName {@code null} for automatic messages (or a deleted user)
 */
public record MessageWithRefsResponse(
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
        Instant createdAt,
        String candidateName,
        boolean candidateHasWhatsapp,
        String jobTitle,
        String sentByName) {

    public static MessageWithRefsResponse of(MessageResponse m, String candidateName, boolean candidateHasWhatsapp, String jobTitle, String sentByName) {
        return new MessageWithRefsResponse(m.id(), m.candidateId(), m.applicationId(), m.channel(), m.direction(), m.subject(), m.body(),
                m.status(), m.templateId(), m.templateCode(), m.sentById(), m.createdAt(), candidateName, candidateHasWhatsapp, jobTitle, sentByName);
    }
}
