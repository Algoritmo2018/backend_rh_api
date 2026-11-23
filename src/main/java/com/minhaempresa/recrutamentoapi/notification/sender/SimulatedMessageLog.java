package com.minhaempresa.recrutamentoapi.notification.sender;

import org.slf4j.Logger;

/** Logging of simulated SMS/WhatsApp messages, masked unless content logging is enabled (dev). */
final class SimulatedMessageLog {

    private SimulatedMessageLog() {}

    static void log(Logger log, String channel, OutboundNotification notification, boolean logContent) {
        if (logContent) {
            log.info("[simulated {}] to {}: {}", channel, notification.recipient(), notification.textBody());
        } else {
            log.info("[simulated {}] to {} ({} characters, content hidden)", channel, mask(notification.recipient()), notification.textBody().length());
        }
    }

    /** {@code +244923456789} → {@code +244******789}. */
    static String mask(String phone) {
        if (phone == null || phone.length() <= 7) {
            return "***";
        }
        return phone.substring(0, 4) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 3);
    }
}
