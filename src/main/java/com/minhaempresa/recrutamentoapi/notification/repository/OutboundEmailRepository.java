package com.minhaempresa.recrutamentoapi.notification.repository;

import com.minhaempresa.recrutamentoapi.notification.entity.OutboundEmail;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundEmailRepository extends JpaRepository<OutboundEmail, UUID> {

    /**
     * The oldest due pending email, locked until the transaction ends. {@code SKIP LOCKED}: another
     * instance (or run) takes the next one instead of waiting, so each email is processed by one worker.
     */
    @Query(value = """
            SELECT * FROM outbound_emails
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    Optional<OutboundEmail> claimNextDue(@Param("now") Instant now);

    /** Retention: delivered emails (their bodies have personal data) and old failures. */
    @Modifying
    @Query("DELETE FROM OutboundEmail e WHERE (e.status = com.minhaempresa.recrutamentoapi.notification.entity.OutboundEmail.Status.SENT AND e.createdAt < :sentBefore)"
            + " OR (e.status = com.minhaempresa.recrutamentoapi.notification.entity.OutboundEmail.Status.FAILED AND e.createdAt < :failedBefore)")
    int deleteExpired(@Param("sentBefore") Instant sentBefore, @Param("failedBefore") Instant failedBefore);
}
