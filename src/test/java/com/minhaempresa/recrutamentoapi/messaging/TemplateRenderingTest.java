package com.minhaempresa.recrutamentoapi.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.service.TemplateRenderer;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateValidator;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateVariable;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Templates are plain text; values are untrusted data. */
class TemplateRenderingTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void htmlRenderingEscapesTheTemplateAndTheValues() {
        String template = "Olá {{nome}},\nveja <a href=\"https://mau.example\">isto</a> & responda.";
        Map<TemplateVariable, String> values = Map.of(TemplateVariable.FIRST_NAME, "<a href=\"https://phishing.example\">Ana</a>");

        String html = renderer.renderHtml(template, values);

        assertThat(html)
                .contains("Olá &lt;a href=&quot;https://phishing.example&quot;&gt;Ana&lt;/a&gt;,<br>")
                .contains("veja &lt;a href=&quot;https://mau.example&quot;&gt;isto&lt;/a&gt; &amp; responda.")
                .doesNotContain("<a ");
    }

    @Test
    void textRenderingFillsKnownVariablesAndKeepsMissingOnesVisible() {
        String text = renderer.renderText("Olá {{nome}}, entrevista: {{ data_entrevista }}.", Map.of(TemplateVariable.FIRST_NAME, "Edna"));

        assertThat(text).isEqualTo("Olá Edna, entrevista: {{ data_entrevista }}.");
    }

    @Test
    void valuesWithRegexCharactersAreInsertedLiterally() {
        assertThat(renderer.renderText("Salário: {{salario}}", Map.of(TemplateVariable.SALARY, "$1 000 \\ USD"))).isEqualTo("Salário: $1 000 \\ USD");
    }

    @Test
    void validatorRejectsHtmlTagsButNotComparisons() {
        assertThat(TemplateValidator.validateOfferTemplate("Clique <a href='x'>aqui</a>")).extracting(FieldError::messageKey).containsExactly("template.html-not-allowed");
        assertThat(TemplateValidator.validateOfferTemplate("Linha<br/>quebrada")).hasSize(1);
        assertThat(TemplateValidator.validateOfferTemplate("Comentário <!-- escondido -->")).hasSize(1);
        assertThat(TemplateValidator.validateOfferTemplate("Salário < 500 000 Kz e bónus > 0")).isEmpty();
    }

    @Test
    void validatorChecksVariablesSmsLengthAndEmailSubject() {
        List<FieldError> unknown = TemplateValidator.validateMessageTemplate(MessageChannel.WHATSAPP, null, "Olá {{nome}} {{x_y}}");
        assertThat(unknown).singleElement().satisfies(e -> {
            assertThat(e.messageKey()).isEqualTo("template.unknown-variable");
            assertThat(e.args()).containsExactly("{{x_y}}");
        });
        assertThat(TemplateValidator.validateMessageTemplate(MessageChannel.SMS, null, "a".repeat(320))).isEmpty();
        assertThat(TemplateValidator.validateMessageTemplate(MessageChannel.SMS, null, "a".repeat(321))).extracting(FieldError::field).containsExactly("body");
        assertThat(TemplateValidator.validateMessageTemplate(MessageChannel.EMAIL, " ", "Corpo")).extracting(FieldError::field).containsExactly("subject");
    }

    @Test
    void everyFrontendVariableIsKnown() {
        for (String name : List.of("nome", "nome_completo", "vaga", "empresa", "data_entrevista", "local_entrevista", "link", "recrutador", "cargo", "salario", "data_inicio", "beneficios")) {
            assertThat(TemplateVariable.fromPlaceholder(name)).as(name).isPresent();
        }
    }
}
