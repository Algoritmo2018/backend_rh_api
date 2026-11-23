package com.minhaempresa.recrutamentoapi.auth.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One refresh token of a login session ("family"). Only the SHA-256 hash is stored. Each refresh
 * rotates it: the old one gets {@code rotatedAt}/{@code revokedAt} and points to its replacement.
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "family_created_at", nullable = false, updatable = false)
    private Instant familyCreatedAt;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(UUID userId, UUID familyId, Instant familyCreatedAt, String tokenHash, Instant createdAt, Instant expiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.familyId = familyId;
        this.familyCreatedAt = familyCreatedAt;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean wasRotated() {
        return rotatedAt != null;
    }

    public void rotate(UUID replacementId, Instant now) {
        rotatedAt = now;
        revokedAt = now;
        replacedById = replacementId;
    }
}
