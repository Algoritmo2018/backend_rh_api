package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import com.minhaempresa.recrutamentoapi.client.event.ContactRequestReceivedEvent;
import com.minhaempresa.recrutamentoapi.client.event.JobRequestCreatedEvent;
import com.minhaempresa.recrutamentoapi.client.event.JobRequestReviewedEvent;
import com.minhaempresa.recrutamentoapi.notification.email.ActionEmail;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.notification.service.NotificationService;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.web.FrontendLinks;
import com.minhaempresa.recrutamentoapi.user.service.UserContact;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Client activity (purpose {@code clients}): new job requests and public contact requests (email and bell
 * to every active administrator), and the review of a job request (email and bell to the client user who
 * made it). Emails go through the outbox and the bell is written in the same transaction as the event.
 * Values typed by users (names, titles, messages) are escaped by the email templates.
 */
@Component
@RequiredArgsConstructor
public class ClientNotificationListener {

    private final EmailOutbox outbox;
    private final NotificationService notifications;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onJobRequestCreated(JobRequestCreatedEvent event) {
        List<UserContact> admins = userService.activeAdministrators();
        String link = links.clientJobRequests(event.clientId());
        for (UserContact admin : admins) {
            queue(admin, new ActionEmail(
                    messages.get("email.job-request-created.subject"),
                    greeting(admin),
                    messages.get("email.job-request-created.body", event.clientName(), event.title()),
                    messages.get("email.job-request-created.action"),
                    link,
                    messages.get("email.job-request-created.note")));
        }
        notifications.notify(
                admins.stream().map(UserContact::id).toList(),
                NotificationType.JOB_REQUEST_CREATED,
                messages.get("notification.job-request-created.title"),
                messages.get("notification.job-request-created.body", event.clientName(), event.title()),
                links.inAppPath(link));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onJobRequestReviewed(JobRequestReviewedEvent event) {
        if (event.requestedById() == null) {
            return;
        }
        userService.findActiveContact(event.requestedById()).ifPresent(user -> {
            boolean converted = event.status() == JobRequestStatus.CONVERTED;
            String prefix = converted ? "email.job-request-converted" : "email.job-request-rejected";
            String body = converted
                    ? messages.get(prefix + ".body", event.title())
                    : messages.get(prefix + ".body", event.title(), event.note());
            String link = links.clientPortalJobRequests();
            queue(user, new ActionEmail(
                    messages.get(prefix + ".subject"),
                    greeting(user),
                    body,
                    messages.get("email.job-request-reviewed.action"),
                    link,
                    messages.get("email.job-request-reviewed.note")));
            String bellPrefix = converted ? "notification.job-request-converted" : "notification.job-request-rejected";
            notifications.notify(
                    List.of(user.id()),
                    NotificationType.JOB_REQUEST_REVIEWED,
                    messages.get(bellPrefix + ".title"),
                    messages.get(bellPrefix + ".body", event.title()),
                    links.inAppPath(link));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onContactRequestReceived(ContactRequestReceivedEvent event) {
        boolean company = event.kind() == ContactRequestKind.COMPANY;
        String body = company
                ? messages.get("email.contact-request.company.body", event.company(), event.name(), event.vacancies(), event.email(), event.phone(), event.message())
                : messages.get("email.contact-request.general.body", event.name(), event.email(), event.phone(), event.message());
        String subject = messages.get(company ? "email.contact-request.company.subject" : "email.contact-request.general.subject");
        List<UserContact> admins = userService.activeAdministrators();
        String link = links.clients();
        for (UserContact admin : admins) {
            queue(admin, new ActionEmail(
                    subject,
                    greeting(admin),
                    body,
                    messages.get("email.contact-request.action"),
                    link,
                    messages.get("email.contact-request.note")));
        }
        notifications.notify(
                admins.stream().map(UserContact::id).toList(),
                NotificationType.CONTACT_REQUEST_RECEIVED,
                messages.get(company ? "notification.contact-request.company.title" : "notification.contact-request.general.title"),
                company
                        ? messages.get("notification.contact-request.company.body", event.company(), event.name())
                        : messages.get("notification.contact-request.general.body", event.name()),
                links.inAppPath(link));
    }

    private String greeting(UserContact user) {
        String name = user.name() == null || user.name().isBlank() ? "" : user.name().trim().split("\\s+")[0];
        return messages.get("email.layout.greeting", name);
    }

    private void queue(UserContact to, ActionEmail email) {
        RenderedEmail rendered = renderer.renderActionEmail(email);
        outbox.enqueue(EmailPurpose.CLIENTS, to.email(), email.subject(), rendered.text(), rendered.html());
    }
}
