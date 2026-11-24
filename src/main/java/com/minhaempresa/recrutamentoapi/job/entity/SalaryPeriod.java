package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Period of a salary range. */
public enum SalaryPeriod implements WireEnum {
    MONTH("month"),
    YEAR("year");

    private final String value;

    SalaryPeriod(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SalaryPeriod fromValue(String value) {
        return WireEnums.fromValue(SalaryPeriod.class, value);
    }
}
