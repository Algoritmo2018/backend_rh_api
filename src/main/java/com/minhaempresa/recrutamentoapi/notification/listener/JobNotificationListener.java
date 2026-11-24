package com.minhaempresa.recrutamentoapi.notification.listener;

import com.minhaempresa.recrutamentoapi.job.event.JobStaffAssignedEvent;
import com.minhaempresa.recrutamentoapi.job.event.JobStaffAssignedEvent.Assignee;
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
 * Emails to staff newly assigned to a job (responsible recruiter or interviewer). Interviewers cannot
 * open the job pipeline, so their link goes to the interview agenda. In-app notifications: stage 7.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final EmailTemplateRenderer renderer;
    private final FrontendLinks links;
    private final Messages messages;
    private final UserService userService;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onStaffAssigned(JobStaffAssignedEvent event) {
        for (Assignee assignee : event.assignees()) {
            userService.findActiveContact(assignee.userId()).ifPresent(user -> send(user, new ActionEmail(
                    messages.get("email.job-assigned.subject"),
                    greeting(user),
                    messages.get(assignee.asRecruiter() ? "email.job-assigned.recruiter.body" : "email.job-assigned.interviewer.body", event.jobTitle()),
                    messages.get(assignee.canOpenJob() ? "email.job-assigned.action" : "email.job-assigned.interviews-action"),
                    assignee.canOpenJob() ? links.job(event.jobId()) : links.interviews(),
                    messages.get("email.job-assigned.note"))));
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
