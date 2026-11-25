package com.minhaempresa.recrutamentoapi.application.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
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

/** Audit trail of the pipeline (frontend {@code StageMove}). Never changed after it is written. */
@Getter
@Entity
@Table(name = "stage_moves")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StageMove extends BaseEntity {

    private static final int MAX_REASON = 500;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10, updatable = false)
    private StageMoveAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20, updatable = false)
    private PipelineStageKey fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", length = 20, updatable = false)
    private PipelineStageKey toStage;

    /** {@code null} = the system or the candidate. */
    @Column(name = "by_user_id", updatable = false)
    private UUID byUserId;

    /** Note of the recruiter or rejection reason; blanked on erasure. */
    @Column(name = "reason", length = MAX_REASON)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StageMove(UUID applicationId, StageMoveAction action, PipelineStageKey fromStage, PipelineStageKey toStage, UUID byUserId, String reason,
            Instant createdAt) {
        this.applicationId = applicationId;
        this.action = action;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.byUserId = byUserId;
        this.reason = reason == null || reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
        this.createdAt = createdAt;
    }
}
