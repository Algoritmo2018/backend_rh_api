package com.minhaempresa.recrutamentoapi.notification.sender;

/**
 * A message to deliver. The content may contain one-time codes or links: never log it outside dev.
 *
 * @param recipient email address or E.164 phone number
 * @param subject email subject ({@code null} for SMS/WhatsApp)
 * @param textBody plain-text body (the whole message for SMS/WhatsApp)
 * @param htmlBody HTML body (email only, may be {@code null})
 */
public record OutboundNotification(NotificationChannel channel, String recipient, String subject, String textBody, String htmlBody) {

    public static OutboundNotification email(String to, String subject, String textBody, String htmlBody) {
        return new OutboundNotification(NotificationChannel.EMAIL, to, subject, textBody, htmlBody);
    }

    public static OutboundNotification sms(String phone, String text) {
        return new OutboundNotification(NotificationChannel.SMS, phone, null, text, null);
    }

    @Override
    public String toString() {
        return "OutboundNotification(channel=" + channel + ", recipient=" + recipient + ")";
    }
}
