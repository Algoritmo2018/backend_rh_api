package com.minhaempresa.recrutamentoapi.notification.email;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
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

    /**
     * A message written by the recruitment team (template or free text), already filled. The text is plain
     * and untrusted: in the HTML version every character is escaped and line breaks become {@code <br>}
     * (same as {@code TemplateRenderer.renderHtml}), so nothing in it can become markup.
     */
    public RenderedEmail renderMessage(String subject, String plainBody) {
        String bodyHtml = HtmlUtils.htmlEscape(plainBody, StandardCharsets.UTF_8.name())
                .replace("\r\n", "\n")
                .replace("\n", "<br>\n");
        Context context = new Context(EMAIL_LOCALE, Map.of("subject", subject, "bodyHtml", bodyHtml, "bodyText", plainBody));
        return new RenderedEmail(
                templateEngine.process("email/message", context),
                templateEngine.process("email/message.txt", context));
    }
}
