package com.minhaempresa.recrutamentoapi.notification.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Placeholder until the WhatsApp Business API is integrated: writes the message to the log. */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class LogWhatsAppNotificationSender implements NotificationSender {

    private final NotificationProperties properties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public void send(OutboundNotification notification) {
        SimulatedMessageLog.log(log, "WhatsApp", notification, properties.logMessageContent());
    }

    @Override
    public boolean isSimulated() {
        return true;
    }
}
