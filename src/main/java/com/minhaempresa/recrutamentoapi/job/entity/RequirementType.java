package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Whether a requirement is mandatory or only desirable. */
public enum RequirementType implements WireEnum {
    MANDATORY("mandatory"),
    DESIRABLE("desirable");

    private final String value;

    RequirementType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RequirementType fromValue(String value) {
        return WireEnums.fromValue(RequirementType.class, value);
    }
}
