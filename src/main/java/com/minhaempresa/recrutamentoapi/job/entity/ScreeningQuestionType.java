package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Answer format of a screening question (frontend {@code ScreeningQuestionType}). */
public enum ScreeningQuestionType implements WireEnum {
    YES_NO("yes_no"),
    SINGLE_CHOICE("single_choice"),
    MULTIPLE_CHOICE("multiple_choice"),
    TEXT("text");

    private final String value;

    ScreeningQuestionType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ScreeningQuestionType fromValue(String value) {
        return WireEnums.fromValue(ScreeningQuestionType.class, value);
    }
}
