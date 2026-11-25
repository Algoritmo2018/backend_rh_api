package com.minhaempresa.recrutamentoapi.interview.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** How the interview takes place (frontend {@code InterviewMode}). */
public enum InterviewMode implements WireEnum {
    ONSITE("onsite"),
    VIDEO("video"),
    PHONE("phone");

    private final String value;

    InterviewMode(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static InterviewMode fromValue(String value) {
        return WireEnums.fromValue(InterviewMode.class, value);
    }
}
