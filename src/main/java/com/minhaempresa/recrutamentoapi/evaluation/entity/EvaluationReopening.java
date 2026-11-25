package com.minhaempresa.recrutamentoapi.evaluation.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A4: an administrator reopened a submitted evaluation (history, never changed; the reason is blanked on erasure). */
@Getter
@Entity
@Table(name = "evaluation_reopenings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationReopening extends BaseEntity {

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "reopened_by", updatable = false)
    private UUID reopenedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reopened_at", nullable = false, updatable = false)
    private Instant reopenedAt;

    public EvaluationReopening(UUID evaluationId, UUID reopenedBy, String reason, Instant reopenedAt) {
        this.evaluationId = evaluationId;
        this.reopenedBy = reopenedBy;
        this.reason = reason;
        this.reopenedAt = reopenedAt;
    }

    /** Erasure: the reason is free text. */
    public void eraseReason() {
        reason = null;
    }
}
