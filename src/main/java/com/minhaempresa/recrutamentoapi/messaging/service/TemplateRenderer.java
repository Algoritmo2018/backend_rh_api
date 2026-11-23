package com.minhaempresa.recrutamentoapi.messaging.service;

import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateVariable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Fills {@code {{variables}}} of message and offer templates.
 *
 * <p>Templates are plain text and variable values (candidate name, job title…) are untrusted data. For
 * email, the whole result (template text and values) is HTML-escaped and line breaks become
 * {@code <br>}: nothing from a template or a value can ever become markup.
 *
 * <p>Variables without a value stay visible ({@code {{data_entrevista}}}), as in the frontend, so the
 * user notices them.
 */
@Component
public class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    /** Plain text (SMS, WhatsApp, email text part, offer letter). */
    public String renderText(String template, Map<TemplateVariable, String> values) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = TemplateVariable.fromPlaceholder(matcher.group(1))
                    .map(values::get)
                    .filter(v -> !v.isEmpty())
                    .orElse(matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** HTML body for email: everything escaped, line breaks as {@code <br>}. */
    public String renderHtml(String template, Map<TemplateVariable, String> values) {
        return HtmlUtils.htmlEscape(renderText(template, values), "UTF-8")
                .replace("\r\n", "\n")
                .replace("\n", "<br>\n");
    }
}
