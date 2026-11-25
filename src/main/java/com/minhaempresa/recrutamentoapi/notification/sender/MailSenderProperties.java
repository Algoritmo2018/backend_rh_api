package com.minhaempresa.recrutamentoapi.notification.sender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email settings ({@code app.mail.*}). The default SMTP server is {@code spring.mail.*}; the accounts
 * configured by administrators (settings) take precedence per purpose.
 *
 * @param from default sender when no account is configured, e.g. {@code FEH OIRAM <nao-responder@fehoiram.co.ao>}
 * @param blockPrivateHosts refuse SMTP accounts whose host resolves to loopback, link-local or private
 *     networks ({@code true} in production)
 * @param outbox queued emails
 * @param directRetryDelays waits between the in-memory attempts of emails that never go through the outbox
 *     (secret links: invitation, password reset, email verification); its size + 1 = attempts (default 3)
 */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record MailSenderProperties(@NotBlank String from, boolean blockPrivateHosts, @Valid @NotNull Outbox outbox, List<Duration> directRetryDelays) {

    public MailSenderProperties {
        directRetryDelays = directRetryDelays == null ? List.of(Duration.ofSeconds(5), Duration.ofSeconds(30)) : List.copyOf(directRetryDelays);
    }

    /**
     * @param pollInterval how often pending emails are sent
     * @param batchSize emails sent per run
     * @param retryDelays wait before each new attempt (its size + 1 = maximum attempts)
     * @param sentRetention delivered emails are deleted after this (their body has personal data)
     * @param failedRetention undelivered emails are deleted after this
     */
    public record Outbox(
            @NotNull Duration pollInterval,
            @Min(1) int batchSize,
            @NotEmpty List<Duration> retryDelays,
            @NotNull Duration sentRetention,
            @NotNull Duration failedRetention) {

        public int maxAttempts() {
            return retryDelays.size() + 1;
        }
    }
}
