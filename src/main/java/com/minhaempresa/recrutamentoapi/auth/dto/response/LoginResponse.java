package com.minhaempresa.recrutamentoapi.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import java.time.Instant;

/**
 * Result of {@code POST /auth/login}: either a session ({@code twoFactorRequired=false} plus the
 * {@link SessionResponse} fields) or, for accounts with two-step verification, a challenge to complete
 * with {@code POST /auth/login/two-factor}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean twoFactorRequired,
        UserResponse user,
        String token,
        Instant expiresAt,
        String challengeToken,
        Long challengeExpiresInSeconds) {

    public static LoginResponse session(SessionResponse session) {
        return new LoginResponse(false, session.user(), session.token(), session.expiresAt(), null, null);
    }

    public static LoginResponse challenge(String challengeToken, long expiresInSeconds) {
        return new LoginResponse(true, null, null, null, challengeToken, expiresInSeconds);
    }
}
