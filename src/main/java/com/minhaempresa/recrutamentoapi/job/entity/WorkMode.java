package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Where the work is done (frontend {@code WorkMode}). */
public enum WorkMode implements WireEnum {
    ONSITE("onsite"),
    REMOTE("remote"),
    HYBRID("hybrid");

    private final String value;

    WorkMode(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WorkMode fromValue(String value) {
        return WireEnums.fromValue(WorkMode.class, value);
    }
}
