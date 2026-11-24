package com.minhaempresa.recrutamentoapi.notification.sender;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification settings ({@code app.notifications.*}).
 *
 * @param allowSimulatedSms features that depend on SMS (two-step verification) may be used with the
 *     log-only sender (dev/test only)
 * @param logMessageContent log the full content of simulated messages and sent emails (dev only: it
 *     includes codes and links)
 * @param readRetention read notifications of the bell are deleted after this
 * @param retention every notification of the bell is deleted after this
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(boolean allowSimulatedSms, boolean logMessageContent, Duration readRetention, Duration retention) {

    public NotificationProperties {
        readRetention = readRetention == null ? Duration.ofDays(90) : readRetention;
        retention = retention == null ? Duration.ofDays(180) : retention;
    }
}
