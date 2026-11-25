package com.minhaempresa.recrutamentoapi.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Match of one job requirement at application time (frontend {@code RequirementMatch}). */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementMatchEntry {

    private static final int MAX_DETAIL = 300;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Column(name = "met", nullable = false)
    private boolean met;

    /** Explanation shown to staff (Portuguese). */
    @Column(name = "detail", nullable = false, length = MAX_DETAIL)
    private String detail;

    public RequirementMatchEntry(UUID requirementId, boolean met, String detail) {
        this.requirementId = requirementId;
        this.met = met;
        this.detail = detail == null ? "" : detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }
}
