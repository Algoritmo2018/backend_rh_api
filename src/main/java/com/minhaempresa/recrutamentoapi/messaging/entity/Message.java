package com.minhaempresa.recrutamentoapi.messaging.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A message to a candidate. A <b>snapshot</b>: the final subject and body as sent (variables filled)
 * and the code of the source template, so editing or deactivating a template never changes history.
 * Only the delivery status changes after it is created.
 */
@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

    private static final int MAX_SUBJECT = 500;
    private static final int MAX_FAILURE = 500;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10, updatable = false)
    private MessageChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10, updatable = false)
    private MessageDirection direction = MessageDirection.OUTBOUND;

    @Column(name = "subject", length = MAX_SUBJECT, updatable = false)
    private String subject;

    /** Plain text as sent (the email HTML is only built for delivery). */
    @Column(name = "body", nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private MessageStatus status = MessageStatus.QUEUED;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    /** Stable code of the source template (kept even if the template is later deleted). */
    @Column(name = "template_code", length = 60, updatable = false)
    private String templateCode;

    @Column(name = "marketing", nullable = false, updatable = false)
    private boolean marketing;

    /** {@code null} = sent automatically by the system. */
    @Column(name = "sent_by", updatable = false)
    private UUID sentBy;

    @Column(name = "failure_reason", length = MAX_FAILURE)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Message(UUID candidateId, UUID applicationId, MessageChannel channel, String subject, String body,
            MessageTemplate template, boolean marketing, UUID sentBy, Instant createdAt) {
        this.candidateId = candidateId;
        this.applicationId = applicationId;
        this.channel = channel;
        this.subject = subject == null || subject.length() <= MAX_SUBJECT ? subject : subject.substring(0, MAX_SUBJECT);
        this.body = body;
        this.templateId = template == null ? null : template.getId();
        this.templateCode = template == null ? null : template.getCode();
        this.marketing = marketing;
        this.sentBy = sentBy;
        this.createdAt = createdAt;
    }

    public void markSent() {
        status = MessageStatus.SENT;
        failureReason = null;
    }

    public void markFailed(String reason) {
        status = MessageStatus.FAILED;
        failureReason = reason == null || reason.length() <= MAX_FAILURE ? reason : reason.substring(0, MAX_FAILURE);
    }

    @Override
    public String toString() {
        return "Message(id=" + getId() + ", channel=" + channel + ", status=" + status + ")";
    }
}
