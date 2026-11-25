package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties.Limit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Applications ({@code app.applications.*}). */
@Validated
@ConfigurationProperties(prefix = "app.applications")
public record ApplicationProperties(@Valid @NotNull RateLimits rateLimits, @Valid @NotNull Bulk bulk) {

    /**
     * @param perIp public form, per IP: generous because of CGNAT
     * @param perEmail per applicant email (public form and logged-in candidates)
     */
    public record RateLimits(@Valid @NotNull Limit perIp, @Valid @NotNull Limit perEmail) {}

    /**
     * Bulk actions of the pipeline (stage 8, adjustment 1): a request with more than one application or candidate.
     *
     * @param maxItems applications (or candidates) per action; more → 422
     * @param maxActionsPerDay bulk actions per user in 24 hours; more → 429 with {@code Retry-After}
     */
    public record Bulk(@Min(1) int maxItems, @Min(1) int maxActionsPerDay) {}
}
