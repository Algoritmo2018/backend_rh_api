package com.minhaempresa.recrutamentoapi.notification.sender;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Routes each notification to the sender of its channel. Senders are injected in {@code @Order}
 * order, and the first one of each channel is used, so a real provider bean with a higher precedence
 * replaces the log-only one.
 */
@Component
public class NotificationDispatcher {

    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final NotificationProperties properties;

    public NotificationDispatcher(List<NotificationSender> orderedSenders, NotificationProperties properties) {
        this.properties = properties;
        for (NotificationSender sender : orderedSenders) {
            senders.putIfAbsent(sender.channel(), sender);
        }
    }

    public void send(OutboundNotification notification) {
        NotificationSender sender = senders.get(notification.channel());
        if (sender == null) {
            throw new IllegalStateException("No sender for channel " + notification.channel());
        }
        sender.send(notification);
    }

    /**
     * True if messages on this channel really reach the user, or if simulated delivery is allowed
     * ({@code app.notifications.allow-simulated-sms}, dev/test).
     */
    public boolean canDeliver(NotificationChannel channel) {
        NotificationSender sender = senders.get(channel);
        return sender != null && (!sender.isSimulated() || properties.allowSimulatedSms());
    }
}
