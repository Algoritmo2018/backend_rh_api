package com.minhaempresa.recrutamentoapi.auth.service;

import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Authentication settings ({@code app.auth.*}).
 *
 * @param tokenPepper server-side key for HMAC of SMS codes (secret, at least 32 characters)
 * @param otpLoginEnabled phone + SMS code login; when off, {@code /auth/otp/*} answer 404
 * @param otpLoginRoles roles allowed to log in by phone
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank @Size(min = 32) String tokenPepper,
        boolean otpLoginEnabled,
        @NotNull Set<UserRole> otpLoginRoles,
        @NotNull Duration otpTtl,
        @Min(1) int otpMaxAttempts,
        @NotNull Duration otpResendCooldown,
        @Min(1) int otpMaxPerPhonePerHour,
        @NotNull Duration twoFactorChallengeTtl,
        @Min(1) int twoFactorMaxAttempts,
        @NotNull Duration passwordResetTtl,
        @NotNull Duration invitationTtl,
        @NotNull Duration emailVerificationTtl,
        @Min(1) int maxFailedLogins,
        @NotNull Duration lockoutDuration,
        @Valid @NotNull RefreshCookie refreshCookie,
        @Valid @NotNull RateLimits rateLimits) {

    /** Attributes of the HttpOnly cookie that carries the refresh token. */
    public record RefreshCookie(@NotBlank String name, @NotBlank String path, boolean secure, @NotBlank String sameSite) {}

    /**
     * Rate limits (in memory, per instance). Per-IP limits are generous because of CGNAT; the
     * per-account, per-email and per-phone limits are the main protection.
     *
     * @param maxEntries upper bound of tracked keys per rule (oldest are evicted)
     * @param login per IP
     * @param otpRequest per IP
     * @param otpVerify per IP (SMS login and two-factor login step)
     * @param forgotPassword per IP
     * @param register per IP
     * @param forgotPasswordPerEmail per email address (counted whether the account exists or not)
     * @param registerPerIdentity per email and per phone number
     * @param passwordConfirmation per user, for operations that check the current password
     */
    public record RateLimits(
            @Min(100) long maxEntries,
            @Valid @NotNull Limit login,
            @Valid @NotNull Limit otpRequest,
            @Valid @NotNull Limit otpVerify,
            @Valid @NotNull Limit forgotPassword,
            @Valid @NotNull Limit register,
            @Valid @NotNull Limit forgotPasswordPerEmail,
            @Valid @NotNull Limit registerPerIdentity,
            @Valid @NotNull Limit passwordConfirmation) {}

    /** At most {@code max} requests per {@code window}. */
    public record Limit(@Min(1) int max, @NotNull Duration window) {}
}
