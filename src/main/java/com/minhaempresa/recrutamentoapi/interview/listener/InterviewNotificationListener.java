package com.minhaempresa.recrutamentoapi.interview.listener;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewChangedEvent;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewChangedEvent.Change;
import com.minhaempresa.recrutamentoapi.interview.event.InterviewNotice;
import com.minhaempresa.recrutamentoapi.interview.service.InterviewCalendar;
import com.minhaempresa.recrutamentoapi.interview.service.InterviewTexts;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageTemplateResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageTemplateService;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateVariable;
import com.minhaempresa.recrutamentoapi.notification.email.ActionEmail;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import com.minhaempresa.recrutamentoapi.notification.sender.EmailAttachment;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.notification.service.NotificationService;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.web.FrontendLinks;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserContact;
import com.minhaempresa.recrutamentoapi.user.service.UserReference;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notices of an interview change, in the transaction of the change (outbox, bells, messages):
 *
 * <ul>
 *   <li><b>Candidate</b> (when asked, never on erasure): invitation (email {@code interview-invite-email} with the
 *       {@code .ics}, plus WhatsApp {@code interview-invite-wa} when the candidate has WhatsApp), "choose a time"
 *       ({@code interview-slots-email}; only when the candidate chooses, A1) or cancellation
 *       ({@code interview-cancelled-email} with the cancellation {@code .ics}). Through {@code MessageService}:
 *       {@code automatic}, {@code sent_by} = the acting user, kept in the conversation.
 *   <li><b>Interviewers</b> (except the actor): bell and email (purpose {@code system}) with the {@code .ics}; the job's
 *       recruiter is also told when a slot is chosen.
 *   <li><b>Client participants</b> (client interviews): bell and email (purpose {@code clients}) with the {@code .ics},
 *       never with the candidate's name (D9-5).
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class InterviewNotificationListener {

    private enum Notice {
        ASSIGNED("assigned", NotificationType.INTERVIEW_ASSIGNED),
        CHOOSE("choose", NotificationType.INTERVIEW_ASSIGNED),
        UPDATED("updated", NotificationType.INTERVIEW_UPDATED),
        CANCELLED("cancelled", NotificationType.INTERVIEW_CANCELLED);

        final String key;
        final NotificationType type;

        Notice(String key, NotificationType type) {
            this.key = key;
            this.type = type;
        }
    }

    private final MessageService messageService;
    private final MessageTemplateService templates;
    private final CandidateService candidateService;
    private final NotificationService notifications;
    private final EmailOutbox outbox;
    private final EmailTemplateRenderer renderer;
    private final UserService userService;
    private final FrontendLinks links;
    private final Messages messages;
    private final InterviewCalendar calendar;
    private final InterviewTexts texts;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onChanged(InterviewChangedEvent event) {
        InterviewNotice interview = event.interview();
        EmailAttachment invitation = calendar.attachment(interview, false);
        EmailAttachment cancellation = calendar.attachment(interview, true);
        if (event.notifyCandidate() && !event.anonymous()) {
            tellCandidate(event, event.change() == Change.CANCELLED ? cancellation : invitation);
        }
        Map<UUID, UserReference> removed = userService.referencesOf(event.removedUsers());

        // Interviewers (and the job's recruiter when a slot is chosen)
        Set<UUID> staff = new LinkedHashSet<>(interview.interviewerIds());
        if (event.change() == Change.SLOT_CHOSEN && interview.recruiterId() != null) {
            staff.add(interview.recruiterId());
        }
        for (UUID userId : staff) {
            Notice notice = noticeFor(event, userId);
            if (notice != null && !userId.equals(event.actorId())) {
                tellStaff(userId, notice, event, notice == Notice.CANCELLED ? cancellation : invitation);
            }
        }
        removed.forEach((userId, user) -> {
            if (user.role() != UserRole.CLIENT && !userId.equals(event.actorId())) {
                tellStaff(userId, Notice.CANCELLED, event, cancellation);
            }
        });

        // Client participants: client interviews only, never with the candidate's name
        if (interview.type() == InterviewType.CLIENT) {
            for (UUID userId : interview.clientParticipantIds()) {
                Notice notice = noticeFor(event, userId);
                if (notice == Notice.ASSIGNED && event.change() == Change.SLOTS_PROPOSED) {
                    notice = Notice.CHOOSE;
                }
                if (notice != null) {
                    tellClient(userId, notice, event, notice == Notice.CANCELLED ? cancellation : invitation);
                }
            }
            removed.forEach((userId, user) -> {
                if (user.role() == UserRole.CLIENT) {
                    tellClient(userId, Notice.CANCELLED, event, cancellation);
                }
            });
        }
    }

    /** New people are invited; the others are told of a cancellation or a change of time/place. */
    private static Notice noticeFor(InterviewChangedEvent event, UUID userId) {
        if (event.change() == Change.CANCELLED) {
            return Notice.CANCELLED;
        }
        if (event.addedInterviewers().contains(userId)) {
            return Notice.ASSIGNED;
        }
        return event.scheduleChanged() ? Notice.UPDATED : null;
    }

    // =====================================================================================

    private void tellCandidate(InterviewChangedEvent event, EmailAttachment attachment) {
        InterviewNotice interview = event.interview();
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.put(TemplateVariable.INTERVIEW_DATE, texts.when(interview.startsAt()));
        values.put(TemplateVariable.INTERVIEW_LOCATION, texts.where(interview.mode(), interview.location(), interview.meetingUrl()));
        switch (event.change()) {
            case CANCELLED -> send("interview-cancelled-email", event, values, attachment);
            case SLOTS_PROPOSED -> {
                if (interview.type() != InterviewType.CLIENT) {
                    // A client interview: the client chooses, the candidate gets the fixed time later (A1)
                    send("interview-slots-email", event, values, null);
                }
            }
            default -> {
                if (interview.startsAt() != null) {
                    send("interview-invite-email", event, values, attachment);
                    if (candidateService.getContact(interview.candidateId()).hasWhatsapp()) {
                        send("interview-invite-wa", event, values, null);
                    }
                }
            }
        }
    }

    private void send(String templateCode, InterviewChangedEvent event, Map<TemplateVariable, String> values, EmailAttachment attachment) {
        MessageTemplateResponse template = templates.findActiveByCode(templateCode)
                .orElseThrow(() -> new IllegalStateException("System template missing: " + templateCode));
        InterviewNotice interview = event.interview();
        MessageChannel channel = template.channel();
        messageService.sendFromTemplate(template.id(), interview.candidateId(), interview.applicationId(), channel, values, event.actorId(), true, attachment);
    }

    private void tellStaff(UUID userId, Notice notice, InterviewChangedEvent event, EmailAttachment attachment) {
        userService.findActiveContact(userId).ifPresent(user -> {
            InterviewNotice interview = event.interview();
            String candidate = event.anonymous() || interview.candidateName() == null ? messages.get("interview.notice.someone") : interview.candidateName();
            String when = texts.when(interview.startsAt());
            String title = messages.get("interview.notice." + notice.key + ".title");
            String body = messages.get("interview.notice." + notice.key + ".body", candidate, interview.jobTitle(), when);
            String link = links.interviews();
            queue(EmailPurpose.SYSTEM, user, title, body, link, attachment);
            notifications.notify(List.of(user.id()), notice.type, title, body, links.inAppPath(link));
        });
    }

    private void tellClient(UUID userId, Notice notice, InterviewChangedEvent event, EmailAttachment attachment) {
        userService.findActiveContact(userId).ifPresent(user -> {
            InterviewNotice interview = event.interview();
            String when = texts.when(interview.startsAt());
            String title = messages.get("interview.client-notice." + notice.key + ".title");
            String body = messages.get("interview.client-notice." + notice.key + ".body", interview.jobTitle(), when);
            String link = links.clientPortalInterviews();
            queue(EmailPurpose.CLIENTS, user, title, body, link, attachment);
            notifications.notify(List.of(user.id()), notice.type, title, body, links.inAppPath(link));
        });
    }

    private void queue(EmailPurpose purpose, UserContact user, String title, String body, String link, EmailAttachment attachment) {
        String name = user.name() == null || user.name().isBlank() ? "" : user.name().trim().split("\\s+")[0];
        ActionEmail email = new ActionEmail(title, messages.get("email.layout.greeting", name), body, messages.get("interview.notice.action"), link,
                messages.get(attachment == null ? "interview.notice.note" : "interview.notice.note-calendar"));
        RenderedEmail rendered = renderer.renderActionEmail(email);
        outbox.enqueue(purpose, user.email(), email.subject(), rendered.text(), rendered.html(), null, null, attachment);
    }
}
