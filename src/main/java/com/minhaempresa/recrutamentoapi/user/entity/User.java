package com.minhaempresa.recrutamentoapi.user.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Account of any role (candidate, internal staff, client-portal user). */
@Getter
@Setter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Login identifier, always lower case. Staff and client emails never change; a candidate's changes only
     * through a confirmed email change ({@link #changeEmail}).
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "email", nullable = false, length = 160)
    private String email;

    /** E.164 ({@code +244923456789}); unique (used by the SMS-code login). */
    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    /** Client company of a {@code CLIENT} user. */
    @Column(name = "client_id")
    private UUID clientId;

    /** Candidate profile of a {@code CANDIDATE} user (linked in stage 6). */
    @Column(name = "candidate_id")
    private UUID candidateId;

    /** {@code null} until the invitation is accepted. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Setter(AccessLevel.NONE)
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Setter(AccessLevel.NONE)
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Setter(AccessLevel.NONE)
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public User(String name, String email, String phone, UserRole role) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a wrong password; after {@code maxAttempts} in a row the account is locked for {@code lockout}. */
    public void registerFailedLogin(int maxAttempts, Duration lockout, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockout);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** New password (already hashed); also lifts a lockout. */
    public void changePassword(String newPasswordHash) {
        passwordHash = newPasswordHash;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /** New login email (lower case), after the owner confirmed it through the link sent to it. */
    public void changeEmail(String newEmail) {
        email = newEmail;
    }

    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
    }
}
