package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Type of employment contract (frontend {@code ContractType}). */
public enum ContractType implements WireEnum {
    PERMANENT("permanent"),
    FIXED_TERM("fixed_term"),
    TEMPORARY("temporary"),
    INTERNSHIP("internship"),
    SERVICE("service");

    private final String value;

    ContractType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ContractType fromValue(String value) {
        return WireEnums.fromValue(ContractType.class, value);
    }
}
