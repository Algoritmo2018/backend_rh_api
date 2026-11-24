package com.minhaempresa.recrutamentoapi.user.service;

import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal view of an account for the auth module (login, token claims). Never returned by a
 * controller: it contains the password hash.
 */
public record UserCredentials(
        UUID id,
        String name,
        String email,
        String phone,
        UserRole role,
        String passwordHash,
        boolean active,
        boolean twoFactorEnabled,
        Instant lockedUntil,
        UUID clientId,
        UUID candidateId) {

    /** Same credentials, but inactive (e.g. a client user of an inactive company). */
    public UserCredentials asInactive() {
        return new UserCredentials(id, name, email, phone, role, passwordHash, false, twoFactorEnabled, lockedUntil, clientId, candidateId);
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Override
    public String toString() {
        return "UserCredentials(id=" + id + ", role=" + role + ")";
    }
}
