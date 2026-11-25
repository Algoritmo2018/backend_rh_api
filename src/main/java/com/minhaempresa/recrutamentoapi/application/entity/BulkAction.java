package com.minhaempresa.recrutamentoapi.application.entity;

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
 * A bulk action of the pipeline (more than one application or candidate in one request), counted for the daily
 * limit per user (stage 8, adjustment 1). Never changed after it is written.
 */
@Getter
@Entity
@Table(name = "application_bulk_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulkAction extends BaseEntity {

    public enum Kind {
        MOVE,
        REJECT,
        ADD_TO_JOB
    }

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private Kind action;

    @Column(name = "items", nullable = false, updatable = false)
    private int items;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BulkAction(UUID userId, Kind action, int items, Instant createdAt) {
        this.userId = userId;
        this.action = action;
        this.items = items;
        this.createdAt = createdAt;
    }
}
