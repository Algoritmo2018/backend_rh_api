package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.auth.event.EmailVerificationRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.event.PasswordResetRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.event.SmsCodeRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties;
import com.minhaempresa.recrutamentoapi.notification.email.ActionEmail;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import com.minhaempresa.recrutamentoapi.notification.service.DirectEmailSender;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.web.FrontendLinks;
import com.minhaempresa.recrutamentoapi.user.event.SecurityAlertEvent;
import com.minhaempresa.recrutamentoapi.user.event.UserInvitedEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Account emails and SMS codes, all of purpose {@code system}.
 *
 * <ul>
 *   <li>Secret links (invitation, password reset, email verification) and SMS codes: after the commit,
 *       asynchronously, sent directly ({@link DirectEmailSender}, attempts in memory) and never stored.
 *   <li>Security alerts: queued in the outbox in the transaction of the change (guaranteed delivery).
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final DirectEmailSender directSender;
    private final EmailOutbox outbox;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final AuthProperties authProperties;
    private final AppProperties appProperties;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onUserInvited(UserInvitedEvent event) {
        sendSecretLinkEmail(event.email(), "email.invitation", event.name(), links.invitation(event.token()),
                authProperties.invitationTtl().toDays());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        sendSecretLinkEmail(event.email(), "email.password-reset", event.name(), links.passwordReset(event.token()),
                authProperties.passwordResetTtl().toHours());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        sendSecretLinkEmail(event.email(), "email.email-verification", event.name(), links.emailVerification(event.token()),
                authProperties.emailVerificationTtl().toHours());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSmsCodeRequested(SmsCodeRequestedEvent event) {
        String key = switch (event.purpose()) {
            case LOGIN -> "sms.login-code";
            case TWO_FACTOR -> "sms.two-factor-code";
            case PHONE_CHANGE -> "sms.phone-change-code";
        };
        String text = messages.get(key, event.code(), event.validity().toMinutes());
        OutboundNotification sms = OutboundNotification.sms(event.phone(), text);
        try {
            dispatcher.send(sms);
        } catch (RuntimeException e) {
            log.error("Could not deliver {}", sms, e);
        }
    }

    /**
     * Tells the owner what changed and when (in Angola time), with a button to reset the password in
     * case it was not them. Queued before the commit: no alert for a change that rolled back.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onSecurityAlert(SecurityAlertEvent event) {
        String kind = event.kind().name().toLowerCase(Locale.ROOT).replace('_', '-');
        String when = DateTimeFormatter.ofPattern(messages.get("format.date-time"))
                .withZone(appProperties.defaults().timeZone())
                .format(event.occurredAt());
        ActionEmail email = new ActionEmail(
                messages.get("email.security-alert.subject"),
                messages.get("email.layout.greeting", firstName(event.name())),
                messages.get("email.security-alert." + kind, when),
                messages.get("email.security-alert.action"),
                links.forgotPassword(),
                messages.get("email.security-alert.note"));
        RenderedEmail rendered = renderer.renderActionEmail(email);
        outbox.enqueue(EmailPurpose.SYSTEM, event.email(), email.subject(), rendered.text(), rendered.html());
    }

    /**
     * @param prefix message-key prefix: {@code .subject}, {@code .body}, {@code .action}, {@code .expiry}
     */
    private void sendSecretLinkEmail(String to, String prefix, String name, String url, long validity) {
        ActionEmail email = new ActionEmail(
                messages.get(prefix + ".subject"),
                messages.get("email.layout.greeting", firstName(name)),
                messages.get(prefix + ".body"),
                messages.get(prefix + ".action"),
                url,
                messages.get(prefix + ".expiry", validity));
        RenderedEmail rendered = renderer.renderActionEmail(email);
        directSender.send(OutboundNotification.email(EmailPurpose.SYSTEM, to, email.subject(), rendered.text(), rendered.html()));
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
