package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import com.minhaempresa.recrutamentoapi.notification.entity.OutboundEmail;
import com.minhaempresa.recrutamentoapi.notification.repository.OutboundEmailRepository;
import com.minhaempresa.recrutamentoapi.notification.sender.EmailNotificationSender;
import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.web.FrontendLinks;
import com.minhaempresa.recrutamentoapi.user.service.UserContact;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the emails of the outbox. Each email is claimed with {@code FOR UPDATE SKIP LOCKED} in its own
 * transaction, so several instances never send the same row at the same time.
 *
 * <p><b>At least once:</b> if the process stops after the SMTP server accepted an email but before the
 * row is marked sent, the email is sent again later. This rare duplicate is accepted.
 *
 * <p>A failed attempt is retried after {@code app.mail.outbox.retry-delays}; after the last one the email
 * is {@code failed} and the administrators get a notification in the bell.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboundEmailRepository repository;
    private final EmailNotificationSender sender;
    private final NotificationService notifications;
    private final UserService userService;
    private final FrontendLinks links;
    private final Messages messages;
    private final MailSenderProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.mail.outbox.poll-interval}", initialDelayString = "${app.mail.outbox.poll-interval}")
    public void run() {
        try {
            drain();
        } catch (RuntimeException e) {
            log.error("Outbox run failed", e);
        }
    }

    /** Sends up to {@code batch-size} due emails; returns how many were processed. */
    public int drain() {
        int processed = 0;
        while (processed < properties.outbox().batchSize() && Boolean.TRUE.equals(transactions.execute(status -> processNext()))) {
            processed++;
        }
        return processed;
    }

    private boolean processNext() {
        Instant now = clock.instant();
        return repository.claimNextDue(now).map(email -> {
            try {
                sender.send(OutboundNotification.email(email.getPurpose(), email.getRecipient(), email.getSubject(), email.getTextBody(), email.getHtmlBody()));
                email.markSent(clock.instant());
            } catch (RuntimeException e) {
                boolean failed = email.markAttemptFailed(clock.instant(), properties.outbox().retryDelays(), safeError(e));
                log.warn("Email {} attempt {} failed: {}", email.getId(), email.getAttempts(), safeError(e));
                if (failed) {
                    tellAdministrators(email);
                }
            }
            return true;
        }).orElse(false);
    }

    private void tellAdministrators(OutboundEmail email) {
        notifications.notify(
                userService.activeAdministrators().stream().map(UserContact::id).toList(),
                NotificationType.EMAIL_FAILED,
                messages.get("notification.email-failed.title"),
                messages.get("notification.email-failed.body", email.getSubject(), email.getRecipient()),
                links.inAppPath(links.settings()));
    }

    /** Exception type and the first line of its message (never the email content nor a password). */
    private static String safeError(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage().lines().findFirst().orElse("");
        return root.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }
}
