package com.minhaempresa.recrutamentoapi.candidate.entity;

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
 * Proof that a candidate was anonymized (Lei n.º 22/11), WITHOUT personal data: no candidate id, name
 * or email — only when, why, who executed it and how many files were deleted. Immutable.
 */
@Getter
@Entity
@Table(name = "erasure_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErasureLog extends BaseEntity {

    public enum Reason {
        /** The holder asked for it (account deletion, stage 12). */
        HOLDER_REQUEST,
        /** Retention period expired (scheduled job, stage 15). */
        RETENTION
    }

    @Column(name = "erased_at", nullable = false, updatable = false)
    private Instant erasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20, updatable = false)
    private Reason reason;

    /** {@code null} for the scheduled retention job. */
    @Column(name = "executed_by", updatable = false)
    private UUID executedBy;

    @Column(name = "documents_deleted", nullable = false, updatable = false)
    private int documentsDeleted;

    public ErasureLog(Instant erasedAt, Reason reason, UUID executedBy, int documentsDeleted) {
        this.erasedAt = erasedAt;
        this.reason = reason;
        this.executedBy = executedBy;
        this.documentsDeleted = documentsDeleted;
    }
}
