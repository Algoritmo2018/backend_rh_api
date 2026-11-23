package com.minhaempresa.recrutamentoapi.user.dto.response;

import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

/**
 * User as the frontend type {@code User} ({@code types/user.ts}), plus {@code active} and
 * {@code emailVerified}.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        UserRole role,
        String avatarUrl,
        String jobTitle,
        UUID clientId,
        UUID candidateId,
        boolean twoFactorEnabled,
        boolean active,
        boolean emailVerified,
        Instant createdAt) {}
