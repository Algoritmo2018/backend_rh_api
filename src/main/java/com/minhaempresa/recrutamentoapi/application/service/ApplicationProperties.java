package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties.Limit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Applications ({@code app.applications.*}). */
@Validated
@ConfigurationProperties(prefix = "app.applications")
public record ApplicationProperties(@Valid @NotNull RateLimits rateLimits) {

    /**
     * @param perIp public form, per IP: generous because of CGNAT
     * @param perEmail per applicant email (public form and logged-in candidates)
     */
    public record RateLimits(@Valid @NotNull Limit perIp, @Valid @NotNull Limit perEmail) {}
}
