package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Driver license category (frontend {@code DriverLicenseCategory}). */
public enum DriverLicenseCategory implements WireEnum {
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E");

    private final String value;

    DriverLicenseCategory(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DriverLicenseCategory fromValue(String value) {
        return WireEnums.fromValue(DriverLicenseCategory.class, value);
    }
}
