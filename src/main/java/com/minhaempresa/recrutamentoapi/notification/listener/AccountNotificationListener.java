package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.auth.event.EmailVerificationRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.event.PasswordResetRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.event.SmsCodeRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties;
import com.minhaempresa.recrutamentoapi.notification.email.ActionEmail;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
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
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Account emails and SMS codes. Runs after the transaction that requested them commits (nothing is
 * sent if it rolls back) and asynchronously (the request does not wait for the SMTP server). A failed
 * delivery is logged; guaranteed delivery (outbox) is planned for a later stage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final AuthProperties authProperties;
    private final AppProperties appProperties;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onUserInvited(UserInvitedEvent event) {
        sendActionEmail(event.email(), "email.invitation", event.name(), links.invitation(event.token()),
                authProperties.invitationTtl().toDays());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        sendActionEmail(event.email(), "email.password-reset", event.name(), links.passwordReset(event.token()),
                authProperties.passwordResetTtl().toHours());
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        sendActionEmail(event.email(), "email.email-verification", event.name(), links.emailVerification(event.token()),
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
        deliver(OutboundNotification.sms(event.phone(), text));
    }

    /**
     * Tells the owner what changed and when (in Angola time), with a button to reset the password in
     * case it was not them.
     */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSecurityAlert(SecurityAlertEvent event) {
        String kind = event.kind().name().toLowerCase(Locale.ROOT).replace('_', '-');
        String when = DateTimeFormatter.ofPattern(messages.get("format.date-time"))
                .withZone(appProperties.defaults().timeZone())
                .format(event.occurredAt());
        send(event.email(), new ActionEmail(
                messages.get("email.security-alert.subject"),
                messages.get("email.layout.greeting", firstName(event.name())),
                messages.get("email.security-alert." + kind, when),
                messages.get("email.security-alert.action"),
                links.forgotPassword(),
                messages.get("email.security-alert.note")));
    }

    /**
     * @param prefix message-key prefix: {@code .subject}, {@code .body}, {@code .action}, {@code .expiry}
     */
    private void sendActionEmail(String to, String prefix, String name, String url, long validity) {
        send(to, new ActionEmail(
                messages.get(prefix + ".subject"),
                messages.get("email.layout.greeting", firstName(name)),
                messages.get(prefix + ".body"),
                messages.get(prefix + ".action"),
                url,
                messages.get(prefix + ".expiry", validity)));
    }

    private void send(String to, ActionEmail email) {
        RenderedEmail rendered = renderer.renderActionEmail(email);
        deliver(OutboundNotification.email(to, email.subject(), rendered.text(), rendered.html()));
    }

    private void deliver(OutboundNotification notification) {
        try {
            dispatcher.send(notification);
        } catch (RuntimeException e) {
            log.error("Could not deliver {}", notification, e);
        }
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.trim().split("\\s+")[0];
    }
}
