package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.AuditableEntity;
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
 * A consent given by a candidate (Lei n.º 22/11): which purpose, which version of the text, when. Never
 * changed except to record the revocation, so it proves what the person accepted.
 */
@Getter
@Entity
@Table(name = "consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent extends AuditableEntity {

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private ConsentType type;

    /** Job of a {@code job_application} consent (stage 8). */
    @Column(name = "job_id", updatable = false)
    private UUID jobId;

    @Column(name = "granted", nullable = false, updatable = false)
    private boolean granted;

    @Column(name = "text_version", nullable = false, length = 20, updatable = false)
    private String textVersion;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public Consent(UUID candidateId, ConsentType type, UUID jobId, String textVersion, Instant grantedAt) {
        this.candidateId = candidateId;
        this.type = type;
        this.jobId = jobId;
        this.granted = true;
        this.textVersion = textVersion;
        this.grantedAt = grantedAt;
    }

    public boolean isActive() {
        return granted && revokedAt == null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
