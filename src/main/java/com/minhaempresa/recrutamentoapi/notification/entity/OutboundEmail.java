package com.minhaempresa.recrutamentoapi.notification.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An email of the outbox: written with the event that causes it, sent by the worker, retried when the
 * SMTP server fails. Delivery is at least once.
 */
@Getter
@Entity
@Table(name = "outbound_emails")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboundEmail extends BaseEntity {

    public enum Status {
        PENDING,
        SENT,
        /** Every attempt failed. */
        FAILED
    }

    private static final int MAX_ERROR = 500;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 15, updatable = false)
    private EmailPurpose purpose;

    @Column(name = "recipient", nullable = false, length = 254, updatable = false)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 500, updatable = false)
    private String subject;

    @Column(name = "text_body", nullable = false, columnDefinition = "text", updatable = false)
    private String textBody;

    @Column(name = "html_body", columnDefinition = "text", updatable = false)
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public OutboundEmail(EmailPurpose purpose, String recipient, String subject, String textBody, String htmlBody, Instant now) {
        this.purpose = purpose;
        this.recipient = recipient;
        this.subject = subject.length() > 500 ? subject.substring(0, 500) : subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public void markSent(Instant now) {
        attempts++;
        status = Status.SENT;
        sentAt = now;
        lastError = null;
    }

    /**
     * Records a failed attempt: schedules the next one after {@code retryDelays[attempts - 1]}, or marks
     * the email as failed when every attempt was used. Returns {@code true} when it is now failed.
     */
    public boolean markAttemptFailed(Instant now, List<Duration> retryDelays, String error) {
        attempts++;
        lastError = error == null ? null : error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
        if (attempts > retryDelays.size()) {
            status = Status.FAILED;
            return true;
        }
        nextAttemptAt = now.plus(retryDelays.get(attempts - 1));
        return false;
    }
}
