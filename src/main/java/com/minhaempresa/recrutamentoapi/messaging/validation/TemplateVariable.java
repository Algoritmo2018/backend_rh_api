package com.minhaempresa.recrutamentoapi.messaging.validation;

import java.util.Arrays;
import java.util.Optional;

/**
 * Variables of message and offer templates. The placeholder stays in Portuguese because it is user
 * content ({@code {{nome}}}); same list as the frontend {@code TEMPLATE_VARIABLES}.
 */
public enum TemplateVariable {
    FIRST_NAME("nome"),
    FULL_NAME("nome_completo"),
    JOB_TITLE("vaga"),
    COMPANY("empresa"),
    INTERVIEW_DATE("data_entrevista"),
    INTERVIEW_LOCATION("local_entrevista"),
    LINK("link"),
    RECRUITER("recrutador"),
    POSITION("cargo"),
    SALARY("salario"),
    START_DATE("data_inicio"),
    BENEFITS("beneficios");

    private final String placeholder;

    TemplateVariable(String placeholder) {
        this.placeholder = placeholder;
    }

    /** Name inside the braces: {@code nome} for {@code {{nome}}}. */
    public String placeholder() {
        return placeholder;
    }

    public static Optional<TemplateVariable> fromPlaceholder(String name) {
        return Arrays.stream(values()).filter(v -> v.placeholder.equals(name)).findFirst();
    }
}
