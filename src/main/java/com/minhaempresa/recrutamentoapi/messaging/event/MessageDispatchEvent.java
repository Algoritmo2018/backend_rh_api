package com.minhaempresa.recrutamentoapi.messaging.event;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import java.util.UUID;

/**
 * An SMS or WhatsApp message to deliver after the commit (emails go through the outbox instead).
 *
 * @param phone E.164 number (personal data: never logged)
 */
public record MessageDispatchEvent(UUID messageId, MessageChannel channel, String phone, String text) {

    @Override
    public String toString() {
        return "MessageDispatchEvent(messageId=" + messageId + ", channel=" + channel + ")";
    }
}
