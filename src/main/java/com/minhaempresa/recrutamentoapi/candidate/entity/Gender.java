package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Gender of a candidate (frontend {@code Gender}). Never used in the match score or search. */
public enum Gender implements WireEnum {
    F("F"),
    M("M");

    private final String value;

    Gender(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Gender fromValue(String value) {
        return WireEnums.fromValue(Gender.class, value);
    }
}
