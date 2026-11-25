package com.minhaempresa.recrutamentoapi.messaging.dto.response;

import java.util.UUID;

/**
 * One conversation of the inbox: every message with a candidate (newest conversation first).
 *
 * @param messageCount messages exchanged with the candidate
 * @param lastMessage the newest one (preview of the list)
 */
public record ConversationResponse(
        UUID candidateId,
        String candidateName,
        boolean candidateHasWhatsapp,
        long messageCount,
        MessageWithRefsResponse lastMessage) {}
