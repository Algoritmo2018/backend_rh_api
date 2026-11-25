package com.minhaempresa.recrutamentoapi.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.notification.email.ActionEmail;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders every email template (HTML and text) with sample data, and every email the application sends
 * with its real texts, so a broken template or a missing message key fails here and not in production.
 */
@IntegrationTest
class EmailTemplatesTest {

    /** Untrusted values (names, titles typed by users) as they may arrive. */
    static final String HOSTILE = "Ana & Filhos <script>alert(1)</script> \"Lda\"";
    static final String URL = "https://example.ao/software_rh/aceitar-convite?token=abc_DEF-123";
    static final Locale EMAIL_LOCALE = Locale.forLanguageTag("pt-AO");

    @Autowired
    ITemplateEngine templateEngine;

    @Autowired
    EmailTemplateRenderer renderer;

    @Autowired
    Messages messages;

    /**
     * Sample model of each template in {@code templates/email/}. A new template must add its sample here
     * (the test below fails otherwise).
     */
    static final Map<String, Function<String, Map<String, Object>>> SAMPLES = Map.of(
            "action-link", value -> Map.of("email", new ActionEmail("Assunto " + value, "Olá " + value + ",", "Corpo " + value, "Abrir", URL, "Nota " + value)),
            // bodyHtml is always escaped by EmailTemplateRenderer.renderMessage before reaching the template
            "message", value -> Map.of("subject", "Assunto " + value, "bodyHtml", HtmlUtils.htmlEscape("Corpo " + value), "bodyText", "Corpo " + value));

    @Test
    void everyTemplateHasBothVersionsAndRendersItsSample() throws IOException {
        Set<String> names = new TreeSet<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:/templates/email/*.*")) {
            String file = resource.getFilename();
            names.add(file.substring(0, file.lastIndexOf('.')));
        }
        assertThat(names).isNotEmpty();
        assertThat(SAMPLES.keySet()).as("every template needs a sample model in this test").containsAll(names);

        for (String name : names) {
            Context context = new Context(EMAIL_LOCALE, SAMPLES.get(name).apply(HOSTILE));
            String html = templateEngine.process("email/" + name, context);
            String text = templateEngine.process("email/" + name + ".txt", context);

            assertResolved(name + " (html)", html);
            assertResolved(name + " (text)", text);
            // Values are always escaped in the HTML version
            assertThat(html).as(name).doesNotContain("<script>").contains("&lt;script&gt;").contains("Ana &amp; Filhos");
            assertThat(html).as(name).contains("FEH OIRAM");
            assertThat(text).as(name).contains(HOSTILE).doesNotContain("<p").doesNotContain("&amp;");
        }
    }

    @Test
    void everyEmailOfTheApplicationRendersWithItsRealTexts() {
        for (Map.Entry<String, ActionEmail> entry : applicationEmails().entrySet()) {
            ActionEmail email = entry.getValue();
            List<String> texts = List.of(email.subject(), email.greeting(), email.body(), email.actionLabel(), email.note());
            assertThat(texts).as(entry.getKey()).noneMatch(text -> text.matches("^(email|format)\\.[a-z0-9.-]+$"));

            RenderedEmail rendered = renderer.renderActionEmail(email);

            assertResolved(entry.getKey() + " (html)", rendered.html());
            assertResolved(entry.getKey() + " (text)", rendered.text());
            assertThat(rendered.html()).as(entry.getKey()).contains(URL.replace("&", "&amp;")).doesNotContain("<script>");
            assertThat(rendered.text()).as(entry.getKey()).contains(URL).contains(email.body());
        }
    }

    @Test
    void messagesToCandidatesAreEscapedWithLineBreaks() {
        RenderedEmail rendered = renderer.renderMessage("Olá " + HOSTILE, "Linha 1 " + HOSTILE + "\nLinha 2");

        assertResolved("message (html)", rendered.html());
        assertResolved("message (text)", rendered.text());
        assertThat(rendered.html()).doesNotContain("<script>").contains("Linha 1 Ana &amp; Filhos &lt;script&gt;").contains("<br>\nLinha 2");
        assertThat(rendered.html()).contains("Recebeu esta mensagem da equipa de recrutamento da FEH OIRAM.");
        assertThat(rendered.text()).startsWith("Linha 1 " + HOSTILE + "\nLinha 2");
    }

    @Test
    void everyBellTextExists() {
        List<String> keys = new ArrayList<>();
        for (String kind : List.of("job-request-created", "job-request-converted", "job-request-rejected",
                "contact-request.company", "contact-request.general", "job-assigned", "email-failed")) {
            keys.add("notification." + kind + ".title");
            if (!kind.equals("job-assigned")) {
                keys.add("notification." + kind + ".body");
            }
        }
        keys.add("notification.job-assigned.recruiter.body");
        keys.add("notification.job-assigned.interviewer.body");
        keys.add("notification.not-found");
        keys.add("notification.email-failed.sender.body");
        assertThat(keys).allSatisfy(key -> assertThat(messages.get(key, HOSTILE, HOSTILE)).as(key).isNotEqualTo(key).doesNotContain("{"));
    }

    /** The same message keys the listeners use, with hostile values for every argument. */
    Map<String, ActionEmail> applicationEmails() {
        Map<String, ActionEmail> emails = new LinkedHashMap<>();
        String greeting = messages.get("email.layout.greeting", "Ana");
        for (String prefix : List.of("email.invitation", "email.password-reset", "email.email-verification")) {
            emails.put(prefix, new ActionEmail(messages.get(prefix + ".subject"), greeting, messages.get(prefix + ".body"),
                    messages.get(prefix + ".action"), URL, messages.get(prefix + ".expiry", 24)));
        }
        for (String kind : List.of("password-changed", "password-reset", "phone-changed", "two-factor-enabled", "two-factor-disabled")) {
            emails.put("email.security-alert." + kind, new ActionEmail(messages.get("email.security-alert.subject"), greeting,
                    messages.get("email.security-alert." + kind, "25/11/2026 10:30"), messages.get("email.security-alert.action"), URL,
                    messages.get("email.security-alert.note")));
        }
        emails.put("email.job-request-created", new ActionEmail(messages.get("email.job-request-created.subject"), greeting,
                messages.get("email.job-request-created.body", HOSTILE, HOSTILE), messages.get("email.job-request-created.action"), URL,
                messages.get("email.job-request-created.note")));
        emails.put("email.job-request-converted", new ActionEmail(messages.get("email.job-request-converted.subject"), greeting,
                messages.get("email.job-request-converted.body", HOSTILE), messages.get("email.job-request-reviewed.action"), URL,
                messages.get("email.job-request-reviewed.note")));
        emails.put("email.job-request-rejected", new ActionEmail(messages.get("email.job-request-rejected.subject"), greeting,
                messages.get("email.job-request-rejected.body", HOSTILE, HOSTILE), messages.get("email.job-request-reviewed.action"), URL,
                messages.get("email.job-request-reviewed.note")));
        emails.put("email.contact-request.company", new ActionEmail(messages.get("email.contact-request.company.subject"), greeting,
                messages.get("email.contact-request.company.body", HOSTILE, HOSTILE, 5, "a@b.ao", "+244923111222", HOSTILE),
                messages.get("email.contact-request.action"), URL, messages.get("email.contact-request.note")));
        emails.put("email.contact-request.general", new ActionEmail(messages.get("email.contact-request.general.subject"), greeting,
                messages.get("email.contact-request.general.body", HOSTILE, "a@b.ao", "+244923111222", HOSTILE),
                messages.get("email.contact-request.action"), URL, messages.get("email.contact-request.note")));
        for (String role : List.of("recruiter", "interviewer")) {
            emails.put("email.job-assigned." + role, new ActionEmail(messages.get("email.job-assigned.subject"), greeting,
                    messages.get("email.job-assigned." + role + ".body", HOSTILE),
                    messages.get(role.equals("recruiter") ? "email.job-assigned.action" : "email.job-assigned.interviews-action"), URL,
                    messages.get("email.job-assigned.note")));
        }
        return emails;
    }

    /** No unresolved Thymeleaf message ({@code ??key_pt_AO??}) and no leftover expression. */
    static void assertResolved(String what, String output) {
        assertThat(output).as(what).isNotBlank().doesNotContain("??").doesNotContain("${").doesNotContain("#{").doesNotContain("[(");
    }
}
