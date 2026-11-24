package com.minhaempresa.recrutamentoapi.notification.sender;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.service.MailSenderRegistry;
import com.minhaempresa.recrutamentoapi.notification.service.MailSenderRegistry.ResolvedSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email over SMTP, HTML with a plain-text alternative. The account (and sender) comes from the purpose
 * of the email ({@link MailSenderRegistry}); never a fixed sender. Every header value is cleaned of
 * line breaks ({@link MailHeaders}): subjects carry untrusted data such as candidate names.
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final MailSenderRegistry registry;
    private final NotificationProperties notificationProperties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(OutboundNotification notification) {
        ResolvedSender resolved = registry.forPurpose(notification.purpose() == null ? EmailPurpose.SYSTEM : notification.purpose());
        try {
            MimeMessage message = resolved.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, notification.htmlBody() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(resolved.from());
            helper.setTo(MailHeaders.address(notification.recipient(), null));
            if (resolved.replyTo() != null) {
                helper.setReplyTo(resolved.replyTo());
            }
            helper.setSubject(MailHeaders.clean(notification.subject()));
            if (notification.htmlBody() != null) {
                helper.setText(notification.textBody(), notification.htmlBody());
            } else {
                helper.setText(notification.textBody());
            }
            resolved.sender().send(message);
            log.info("Email sent ({})", notification.purpose());
            if (notificationProperties.logMessageContent()) {
                log.info("Email content (dev):\n{}", notification.textBody());
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the email", e);
        }
    }
}
