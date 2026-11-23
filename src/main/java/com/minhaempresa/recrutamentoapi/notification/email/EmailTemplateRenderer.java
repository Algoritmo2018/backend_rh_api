package com.minhaempresa.recrutamentoapi.notification.email;

import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders emails with Thymeleaf ({@code templates/email/}). Layout texts (signature, footer) come from
 * {@code messages.properties} through {@code #{…}} expressions; the email-specific texts arrive already
 * resolved in the model.
 */
@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    /** Language of the emails (Portuguese, Angola). */
    static final Locale EMAIL_LOCALE = Locale.forLanguageTag("pt-AO");

    private final ITemplateEngine templateEngine;

    public record RenderedEmail(String html, String text) {}

    public RenderedEmail renderActionEmail(ActionEmail email) {
        Context context = new Context(EMAIL_LOCALE, Map.of("email", email));
        return new RenderedEmail(
                templateEngine.process("email/action-link", context),
                templateEngine.process("email/action-link.txt", context));
    }
}
