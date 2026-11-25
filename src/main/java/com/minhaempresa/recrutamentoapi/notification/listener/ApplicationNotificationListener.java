package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.application.event.CandidatesSharedEvent;
import com.minhaempresa.recrutamentoapi.application.event.ClientCommentAddedEvent;
import com.minhaempresa.recrutamentoapi.application.event.ApplicationSubmittedEvent;
import com.minhaempresa.recrutamentoapi.application.event.DuplicateApplicationEvent;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Applications (stage 8a), in the transaction of the application:
 *
 * <ul>
 *   <li>New application → bell of the job's responsible recruiter, when that user can still log in (no
 *       email, D8-4).
 *   <li>Public application for a job the email already applied to → a notice to that email (purpose
 *       {@code recruitment}, outbox). The greeting is generic: the name sent with the form is not trusted.
 *   <li>Candidates shared with the client, or a FEH OIRAM comment in the shortlist (stage 8c) → bell and email to
 *       every active portal user of the client company (purpose {@code clients}), never with the candidate's name
 *       (it would outlive an erasure in the mailbox).
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ApplicationNotificationListener {

    private final NotificationService notifications;
    private final EmailOutbox outbox;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onSubmitted(ApplicationSubmittedEvent event) {
        if (event.recruiterId() == null) {
            return;
        }
        userService.findActiveContact(event.recruiterId()).ifPresent(recruiter -> notifications.notify(
                List.of(recruiter.id()),
                NotificationType.APPLICATION_RECEIVED,
                messages.get("notification.application-received.title"),
                messages.get("notification.application-received.body", event.candidateName(), event.jobTitle()),
                links.inAppPath(links.job(event.jobId()))));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onDuplicate(DuplicateApplicationEvent event) {
        ActionEmail email = new ActionEmail(
                messages.get("email.application-duplicate.subject"),
                messages.get("email.application-duplicate.greeting"),
                messages.get("email.application-duplicate.body", event.jobTitle()),
                messages.get("email.application-duplicate.action"),
                links.candidatePortal(),
                messages.get("email.application-duplicate.note"));
        RenderedEmail rendered = renderer.renderActionEmail(email);
        outbox.enqueue(EmailPurpose.RECRUITMENT, event.email(), email.subject(), rendered.text(), rendered.html());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onShared(CandidatesSharedEvent event) {
        tellClient(event.clientId(), event.jobId(), NotificationType.CANDIDATES_SHARED, "candidates-shared", event.jobTitle());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onClientComment(ClientCommentAddedEvent event) {
        tellClient(event.clientId(), event.jobId(), NotificationType.CLIENT_COMMENT, "client-comment", event.jobTitle());
    }

    /** One email per user (single recipient) and one bell each; the text has the job title only. */
    private void tellClient(UUID clientId, UUID jobId, NotificationType type, String key, String jobTitle) {
        List<UserContact> users = userService.activeContactsOfClient(clientId);
        if (users.isEmpty()) {
            return;
        }
        String link = links.clientPortalJob(jobId);
        for (UserContact user : users) {
            String name = user.name() == null || user.name().isBlank() ? "" : user.name().trim().split("\\s+")[0];
            ActionEmail email = new ActionEmail(
                    messages.get("email." + key + ".subject", jobTitle),
                    messages.get("email.layout.greeting", name),
                    messages.get("email." + key + ".body", jobTitle),
                    messages.get("email." + key + ".action"),
                    link,
                    messages.get("email." + key + ".note"));
            RenderedEmail rendered = renderer.renderActionEmail(email);
            outbox.enqueue(EmailPurpose.CLIENTS, user.email(), email.subject(), rendered.text(), rendered.html());
        }
        notifications.notify(users.stream().map(UserContact::id).toList(), type,
                messages.get("notification." + key + ".title"),
                messages.get("notification." + key + ".body", jobTitle),
                links.inAppPath(link));
    }
}
