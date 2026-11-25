package com.minhaempresa.recrutamentoapi.application.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Status of an application (frontend {@code ApplicationStatus}). */
public enum ApplicationStatus implements WireEnum {
    ACTIVE("active"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn"),
    HIRED("hired");

    private final String value;

    ApplicationStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ApplicationStatus fromValue(String value) {
        return WireEnums.fromValue(ApplicationStatus.class, value);
    }
}
