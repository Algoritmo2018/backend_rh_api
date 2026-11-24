package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import com.minhaempresa.recrutamentoapi.client.event.ContactRequestReceivedEvent;
import com.minhaempresa.recrutamentoapi.client.event.JobRequestCreatedEvent;
import com.minhaempresa.recrutamentoapi.client.event.JobRequestReviewedEvent;
import com.minhaempresa.recrutamentoapi.notification.email.ActionEmail;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.web.FrontendLinks;
import com.minhaempresa.recrutamentoapi.user.service.UserContact;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails about client activity: new job requests and public contact requests (to the administrators),
 * and the review of a job request (to the client user who made it). In-app notifications are added in
 * stage 7. Values typed by users (names, titles, messages) are escaped by the email templates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final UserService userService;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onJobRequestCreated(JobRequestCreatedEvent event) {
        for (UserContact admin : userService.activeAdministrators()) {
            send(admin, new ActionEmail(
                    messages.get("email.job-request-created.subject"),
                    greeting(admin),
                    messages.get("email.job-request-created.body", event.clientName(), event.title()),
                    messages.get("email.job-request-created.action"),
                    links.clientJobRequests(event.clientId()),
                    messages.get("email.job-request-created.note")));
        }
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
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
            send(user, new ActionEmail(
                    messages.get(prefix + ".subject"),
                    greeting(user),
                    body,
                    messages.get("email.job-request-reviewed.action"),
                    links.clientPortalJobRequests(),
                    messages.get("email.job-request-reviewed.note")));
        });
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onContactRequestReceived(ContactRequestReceivedEvent event) {
        boolean company = event.kind() == ContactRequestKind.COMPANY;
        String body = company
                ? messages.get("email.contact-request.company.body", event.company(), event.name(), event.vacancies(), event.email(), event.phone(), event.message())
                : messages.get("email.contact-request.general.body", event.name(), event.email(), event.phone(), event.message());
        String subject = messages.get(company ? "email.contact-request.company.subject" : "email.contact-request.general.subject");
        for (UserContact admin : userService.activeAdministrators()) {
            send(admin, new ActionEmail(
                    subject,
                    greeting(admin),
                    body,
                    messages.get("email.contact-request.action"),
                    links.clients(),
                    messages.get("email.contact-request.note")));
        }
    }

    private String greeting(UserContact user) {
        String name = user.name() == null || user.name().isBlank() ? "" : user.name().trim().split("\\s+")[0];
        return messages.get("email.layout.greeting", name);
    }

    private void send(UserContact to, ActionEmail email) {
        try {
            RenderedEmail rendered = renderer.renderActionEmail(email);
            dispatcher.send(OutboundNotification.email(to.email(), email.subject(), rendered.text(), rendered.html()));
        } catch (RuntimeException e) {
            log.error("Could not deliver the email \"{}\" to {}", email.subject(), to, e);
        }
    }
}
