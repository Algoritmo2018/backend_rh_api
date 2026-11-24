package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Purpose of a consent (Lei n.º 22/11; frontend {@code ConsentType}). */
public enum ConsentType implements WireEnum {
    JOB_APPLICATION("job_application"),
    TALENT_POOL("talent_pool"),
    MARKETING("marketing");

    private final String value;

    ConsentType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ConsentType fromValue(String value) {
        return WireEnums.fromValue(ConsentType.class, value);
    }
}
