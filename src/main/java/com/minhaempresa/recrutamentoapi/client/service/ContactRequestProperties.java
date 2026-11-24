package com.minhaempresa.recrutamentoapi.client.service;

import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties.Limit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public contact forms ({@code app.contact.*}). */
@Validated
@ConfigurationProperties(prefix = "app.contact")
public record ContactRequestProperties(@Valid @NotNull RateLimits rateLimits) {

    /**
     * @param perIp generous because of CGNAT (many mobile customers share one IP)
     * @param perEmail the main protection against flooding the administrators
     */
    public record RateLimits(@Valid @NotNull Limit perIp, @Valid @NotNull Limit perEmail) {}
}
