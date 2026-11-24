package com.minhaempresa.recrutamentoapi.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Professional area of a job or job request (frontend {@code JobArea}). The API accepts only the key
 * ({@code oil_gas}), never the Portuguese label shown by the frontend.
 */
public enum JobArea implements WireEnum {
    ADMINISTRATION("administration"),
    FINANCE("finance"),
    LOGISTICS("logistics"),
    TRANSPORT("transport"),
    HSE("hse"),
    OIL_GAS("oil_gas"),
    ENGINEERING("engineering"),
    MAINTENANCE("maintenance"),
    CONSTRUCTION("construction"),
    IT("it");

    private final String value;

    JobArea(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JobArea fromValue(String value) {
        return WireEnums.fromValue(JobArea.class, value);
    }
}
