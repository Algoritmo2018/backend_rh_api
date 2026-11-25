package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.entity.OutboundEmail;
import com.minhaempresa.recrutamentoapi.notification.repository.OutboundEmailRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues an email in the outbox, in the caller's transaction: it is only sent if that transaction
 * commits, and it survives a restart or an SMTP failure ({@link OutboxWorker} retries). Never use it for
 * emails with secret links (password reset, invitation, email verification): those are sent directly
 * and never stored (decision D7-3).
 */
@Service
@RequiredArgsConstructor
public class EmailOutbox {

    private final OutboundEmailRepository repository;
    private final Clock clock;

    @Transactional
    public void enqueue(EmailPurpose purpose, String recipient, String subject, String textBody, String htmlBody) {
        enqueue(purpose, recipient, subject, textBody, htmlBody, null, null);
    }

    /**
     * @param referenceId record the email belongs to: its delivery is published as
     *     {@link com.minhaempresa.recrutamentoapi.notification.event.OutboundEmailStatusEvent}
     * @param notifyUserId user told in the bell if it fails for good (who sent it); {@code null} = the
     *     administrators (system emails, automatic sends)
     */
    @Transactional
    public void enqueue(EmailPurpose purpose, String recipient, String subject, String textBody, String htmlBody, UUID referenceId, UUID notifyUserId) {
        repository.save(new OutboundEmail(purpose, recipient, subject, textBody, htmlBody, clock.instant(), referenceId, notifyUserId));
    }

    /** Deletes the emails of these records, sent or not (erasure of a candidate). */
    @Transactional
    public void deleteByReferences(Collection<UUID> referenceIds) {
        if (!referenceIds.isEmpty()) {
            repository.deleteByReferenceIdIn(referenceIds);
        }
    }
}
