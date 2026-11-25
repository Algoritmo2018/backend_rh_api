package com.minhaempresa.recrutamentoapi.notification.sender;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;

/**
 * A message to deliver. The content may contain one-time codes or links: never log it outside dev.
 *
 * @param purpose kind of email, which chooses the sending account ({@code null} for SMS/WhatsApp)
 * @param recipient email address or E.164 phone number
 * @param subject email subject ({@code null} for SMS/WhatsApp)
 * @param textBody plain-text body (the whole message for SMS/WhatsApp)
 * @param htmlBody HTML body (email only, may be {@code null})
 * @param attachment text attachment (email only, may be {@code null}), e.g. a calendar invitation
 */
public record OutboundNotification(NotificationChannel channel, EmailPurpose purpose, String recipient, String subject, String textBody, String htmlBody,
        EmailAttachment attachment) {

    public static OutboundNotification email(EmailPurpose purpose, String to, String subject, String textBody, String htmlBody) {
        return email(purpose, to, subject, textBody, htmlBody, null);
    }

    public static OutboundNotification email(EmailPurpose purpose, String to, String subject, String textBody, String htmlBody, EmailAttachment attachment) {
        return new OutboundNotification(NotificationChannel.EMAIL, purpose, to, subject, textBody, htmlBody, attachment);
    }

    public static OutboundNotification sms(String phone, String text) {
        return new OutboundNotification(NotificationChannel.SMS, null, phone, null, text, null, null);
    }

    public static OutboundNotification whatsapp(String phone, String text) {
        return new OutboundNotification(NotificationChannel.WHATSAPP, null, phone, null, text, null, null);
    }

    @Override
    public String toString() {
        return "OutboundNotification(channel=" + channel + ", purpose=" + purpose + ")";
    }
}
