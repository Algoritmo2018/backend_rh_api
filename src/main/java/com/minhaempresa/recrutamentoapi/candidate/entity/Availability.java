package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** When a candidate can start (frontend {@code Availability}). */
public enum Availability implements WireEnum {
    IMMEDIATE("immediate"),
    DAYS_15("15_days"),
    DAYS_30("30_days"),
    DAYS_60("60_days"),
    NEGOTIABLE("negotiable");

    private final String value;

    Availability(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Availability fromValue(String value) {
        return WireEnums.fromValue(Availability.class, value);
    }
}
