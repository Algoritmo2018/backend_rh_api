package com.minhaempresa.recrutamentoapi.notification.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Placeholder until an SMS provider is integrated: writes the SMS to the log. Outside dev the content
 * and the number are masked, because SMS carry login codes.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class LogSmsNotificationSender implements NotificationSender {

    private final NotificationProperties properties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(OutboundNotification notification) {
        SimulatedMessageLog.log(log, "SMS", notification, properties.logMessageContent());
    }

    @Override
    public boolean isSimulated() {
        return true;
    }
}
