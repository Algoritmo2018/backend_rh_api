package com.minhaempresa.recrutamentoapi.auth.entity;

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

/** Single-use secret (email link or SMS code). Only hashes are stored. */
@Getter
@Entity
@Table(name = "one_time_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OneTimeToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private OneTimeTokenType type;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "code_hash", length = 64, updatable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /** New address of an {@link OneTimeTokenType#EMAIL_CHANGE} token (lower case); null for the other types. */
    @Column(name = "target_email", length = 160, updatable = false)
    private String targetEmail;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OneTimeToken(UUID userId, OneTimeTokenType type, String tokenHash, String codeHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.type = type;
        this.tokenHash = tokenHash;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Email-change link: bound to the user and to the new address. */
    public static OneTimeToken emailChange(UUID userId, String tokenHash, String targetEmail, Instant createdAt, Instant expiresAt) {
        OneTimeToken token = new OneTimeToken(userId, OneTimeTokenType.EMAIL_CHANGE, tokenHash, null, createdAt, expiresAt);
        token.targetEmail = targetEmail;
        return token;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        usedAt = now;
    }

    /** Counts a code check; returns the number of attempts so far. */
    public int registerAttempt() {
        return ++attempts;
    }
}
