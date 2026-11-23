package com.minhaempresa.recrutamentoapi.notification.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification settings ({@code app.notifications.*}).
 *
 * @param allowSimulatedSms features that depend on SMS (two-step verification) may be used with the
 *     log-only sender (dev/test only)
 * @param logMessageContent log the full content of simulated messages and sent emails (dev only: it
 *     includes codes and links)
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(boolean allowSimulatedSms, boolean logMessageContent) {}
