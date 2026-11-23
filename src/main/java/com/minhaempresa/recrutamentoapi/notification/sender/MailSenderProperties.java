package com.minhaempresa.recrutamentoapi.notification.sender;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email settings ({@code app.mail.*}); the SMTP server itself is configured with {@code spring.mail.*}.
 *
 * @param from sender, e.g. {@code FEH OIRAM <nao-responder@fehoiram.co.ao>}
 */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record MailSenderProperties(@NotBlank String from) {}
