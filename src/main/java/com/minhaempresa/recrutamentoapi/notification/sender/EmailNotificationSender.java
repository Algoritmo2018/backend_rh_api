package com.minhaempresa.recrutamentoapi.notification.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Email over SMTP ({@code spring.mail.*}; Mailpit in dev), HTML with a plain-text alternative. */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final MailSenderProperties mailProperties;
    private final NotificationProperties notificationProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(OutboundNotification notification) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, notification.htmlBody() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from());
            helper.setTo(notification.recipient());
            helper.setSubject(notification.subject());
            if (notification.htmlBody() != null) {
                helper.setText(notification.textBody(), notification.htmlBody());
            } else {
                helper.setText(notification.textBody());
            }
            mailSender.send(message);
            log.info("Email sent to {}: {}", notification.recipient(), notification.subject());
            if (notificationProperties.logMessageContent()) {
                log.info("Email content (dev):\n{}", notification.textBody());
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build email to " + notification.recipient(), e);
        }
    }
}
