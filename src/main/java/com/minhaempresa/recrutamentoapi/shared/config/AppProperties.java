package com.minhaempresa.recrutamentoapi.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-specific settings under the {@code app.*} prefix. Validated at startup, so a missing
 * value fails fast instead of breaking at runtime.
 *
 * @param frontendUrl base URL of the frontend (used to build links in notifications), e.g.
 *     {@code http://localhost:5173/software_rh}
 * @param candidatePortalUrl link to the candidate portal used in message templates
 * @param defaults default values for new records
 * @param cors CORS settings
 * @param jwt JWT signing settings
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotBlank String candidatePortalUrl,
        @Valid @NotNull Defaults defaults,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt) {

    /**
     * Defaults applied to new records.
     *
     * @param nationality default nationality of a new candidate
     * @param language default language of a new candidate
     * @param timeZone time zone used to show dates to users (data is stored in UTC)
     */
    public record Defaults(@NotBlank String nationality, @NotBlank String language, @NotNull ZoneId timeZone) {}

    /**
     * @param allowedOrigins origins allowed to call the API from a browser
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {}

    /**
     * @param secret HMAC key for HS256 (at least 32 characters)
     * @param issuer value of the {@code iss} claim, checked on every request
     * @param accessTokenTtl lifetime of an access token (short: deactivation and role changes only
     *     take effect on the next refresh)
     * @param refreshTokenTtl lifetime of each refresh token (renewed on every rotation)
     * @param refreshFamilyMaxAge absolute lifetime of a login session; after it a new login is required
     * @param refreshReuseGracePeriod reusing an already rotated refresh token within this period (two
     *     concurrent refreshes) is rejected without revoking the session
     */
    public record Jwt(
            @NotBlank @Size(min = 32) String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotNull Duration refreshFamilyMaxAge,
            @NotNull Duration refreshReuseGracePeriod) {}
}
