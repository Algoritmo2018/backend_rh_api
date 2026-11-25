package com.minhaempresa.recrutamentoapi.interview.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Kind of interview (frontend {@code InterviewType}). */
public enum InterviewType implements WireEnum {
    HR("hr"),
    TECHNICAL("technical"),
    CLIENT("client");

    private final String value;

    InterviewType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static InterviewType fromValue(String value) {
        return WireEnums.fromValue(InterviewType.class, value);
    }
}
