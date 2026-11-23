package com.minhaempresa.recrutamentoapi.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Fixed keys of the recruitment pipeline stages (names and order are configurable, keys are not).
 * Used by applications, reports and the candidate/client views.
 */
public enum PipelineStageKey implements WireEnum {
    NEW("new"),
    SCREENING("screening"),
    HR_INTERVIEW("hr_interview"),
    TECHNICAL_TEST("technical_test"),
    CLIENT_INTERVIEW("client_interview"),
    OFFER("offer"),
    HIRED("hired");

    private final String value;

    PipelineStageKey(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PipelineStageKey fromValue(String value) {
        return WireEnums.fromValue(PipelineStageKey.class, value);
    }
}
