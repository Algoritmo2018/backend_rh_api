package com.minhaempresa.recrutamentoapi.notification.sender;

/**
 * Delivers notifications on one channel. Implementations: SMTP email, and log-only SMS/WhatsApp until
 * real providers are integrated. To plug a provider, add a bean for the channel with a higher
 * {@code @Order} (the first sender of each channel wins).
 */
public interface NotificationSender {

    NotificationChannel channel();

    void send(OutboundNotification notification);

    /** True when nothing is actually delivered (log-only implementation). */
    default boolean isSimulated() {
        return false;
    }
}
