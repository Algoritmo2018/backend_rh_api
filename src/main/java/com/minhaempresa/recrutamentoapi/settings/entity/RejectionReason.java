package com.minhaempresa.recrutamentoapi.settings.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reason for rejecting an application. Never deleted (the rejection history must stay readable):
 * deactivated instead. Reasons used by the code ({@code client}, {@code withdrew},
 * {@code declined-offer}) are found by their {@code code}.
 */
@Getter
@Setter
@Entity
@Table(name = "rejection_reasons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RejectionReason extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @Column(name = "code", nullable = false, length = 60, updatable = false)
    private String code;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RejectionReasonCategory category;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "position", nullable = false)
    private int position;

    public RejectionReason(String code, String label, RejectionReasonCategory category, int position) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.position = position;
    }
}
