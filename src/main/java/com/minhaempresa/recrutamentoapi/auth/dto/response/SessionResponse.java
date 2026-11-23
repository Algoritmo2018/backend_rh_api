package com.minhaempresa.recrutamentoapi.auth.dto.response;

import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import java.time.Instant;

/**
 * Logged-in session, same shape as the frontend {@code Session}: {@code token} is the access token.
 * The refresh token is not in the body: it goes in an HttpOnly cookie.
 */
public record SessionResponse(UserResponse user, String token, Instant expiresAt) {}
