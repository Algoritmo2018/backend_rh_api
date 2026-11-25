package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.job.event.JobStaffAssignedEvent;
import com.minhaempresa.recrutamentoapi.job.event.JobStaffAssignedEvent.Assignee;
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
 * Staff newly assigned to a job (responsible recruiter or interviewer): email (purpose {@code system},
 * outbox) and bell, in the transaction of the job change. Interviewers cannot open the job pipeline, so
 * their link goes to the interview agenda.
 */
@Component
@RequiredArgsConstructor
public class JobNotificationListener {

    private final EmailOutbox outbox;
    private final NotificationService notifications;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onStaffAssigned(JobStaffAssignedEvent event) {
        for (Assignee assignee : event.assignees()) {
            userService.findActiveContact(assignee.userId()).ifPresent(user -> {
                String link = assignee.canOpenJob() ? links.job(event.jobId()) : links.interviews();
                String role = assignee.asRecruiter() ? "recruiter" : "interviewer";
                ActionEmail email = new ActionEmail(
                        messages.get("email.job-assigned.subject"),
                        greeting(user),
                        messages.get("email.job-assigned." + role + ".body", event.jobTitle()),
                        messages.get(assignee.canOpenJob() ? "email.job-assigned.action" : "email.job-assigned.interviews-action"),
                        link,
                        messages.get("email.job-assigned.note"));
                RenderedEmail rendered = renderer.renderActionEmail(email);
                outbox.enqueue(EmailPurpose.SYSTEM, user.email(), email.subject(), rendered.text(), rendered.html());
                notifications.notify(
                        List.of(user.id()),
                        NotificationType.JOB_ASSIGNED,
                        messages.get("notification.job-assigned.title"),
                        messages.get("notification.job-assigned." + role + ".body", event.jobTitle()),
                        links.inAppPath(link));
            });
        }
    }

    private String greeting(UserContact user) {
        String name = user.name() == null || user.name().isBlank() ? "" : user.name().trim().split("\\s+")[0];
        return messages.get("email.layout.greeting", name);
    }
}
