package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.sender.EmailNotificationSender;
import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends emails that carry a secret link (invitation, password reset, email verification) without the
 * outbox (decision D7-3): the link is never written to the database. A few attempts are made in memory
 * ({@code app.mail.direct-retry-delays}, 3 by default); if all fail the email is lost and the user asks
 * again (the screens offer "reenviar"). Call it from an asynchronous listener: it waits between attempts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectEmailSender {

    private final EmailNotificationSender sender;
    private final MailSenderProperties properties;

    /** @return {@code true} if one of the attempts was accepted by the SMTP server */
    public boolean send(OutboundNotification email) {
        List<Duration> delays = properties.directRetryDelays();
        for (int attempt = 1; ; attempt++) {
            try {
                sender.send(email);
                return true;
            } catch (RuntimeException e) {
                // Never the content: it holds the secret link
                log.warn("Direct email ({}) attempt {} failed: {}", email.purpose(), attempt, e.getClass().getSimpleName());
                if (attempt > delays.size()) {
                    log.error("Direct email ({}) not delivered after {} attempts", email.purpose(), attempt);
                    return false;
                }
                if (!sleep(delays.get(attempt - 1))) {
                    return false;
                }
            }
        }
    }

    private static boolean sleep(Duration delay) {
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
