package com.minhaempresa.recrutamentoapi.messaging.validation;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Business rules of message and offer templates, same as the frontend ({@code TemplateFormDialog},
 * {@code OfferTemplatesTab}) plus: templates are plain text, so HTML tags are rejected.
 */
public final class TemplateValidator {

    /** Same limit as the frontend: two SMS segments. */
    public static final int SMS_MAX_LENGTH = 320;

    static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
    /** An HTML tag or comment ({@code <a href=…>}, {@code </p>}, {@code <br/>}, {@code <!--}); plain {@code a < b} is fine. */
    private static final Pattern HTML_TAG = Pattern.compile("<!--|</?[a-zA-Z][a-zA-Z0-9-]*(?:\\s[^<>]*)?/?>");

    private TemplateValidator() {}

    /** Rules of a message template; empty when valid. */
    public static List<FieldError> validateMessageTemplate(MessageChannel channel, String subject, String body) {
        List<FieldError> errors = new ArrayList<>();
        if (channel == MessageChannel.EMAIL && (subject == null || subject.trim().length() < 3)) {
            errors.add(new FieldError("subject", "template.subject-required"));
        }
        if (channel == MessageChannel.SMS && body.length() > SMS_MAX_LENGTH) {
            errors.add(new FieldError("body", "template.sms-too-long", List.of(SMS_MAX_LENGTH)));
        }
        if (subject != null) {
            checkText("subject", subject, errors);
        }
        checkText("body", body, errors);
        return errors;
    }

    /** Rules of an offer letter template; empty when valid. */
    public static List<FieldError> validateOfferTemplate(String body) {
        List<FieldError> errors = new ArrayList<>();
        checkText("body", body, errors);
        return errors;
    }

    /** Variables used in a text, in order of appearance (without duplicates). */
    public static Set<String> variablesIn(String text) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static void checkText(String field, String text, List<FieldError> errors) {
        if (HTML_TAG.matcher(text).find()) {
            errors.add(new FieldError(field, "template.html-not-allowed"));
        }
        variablesIn(text).stream()
                .filter(name -> TemplateVariable.fromPlaceholder(name).isEmpty())
                .findFirst()
                .ifPresent(name -> errors.add(new FieldError(field, "template.unknown-variable", List.of("{{" + name + "}}"))));
    }
}
