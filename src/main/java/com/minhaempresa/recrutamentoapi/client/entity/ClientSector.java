package com.minhaempresa.recrutamentoapi.client.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Business sector of a client company (frontend {@code ClientSector}). */
public enum ClientSector implements WireEnum {
    OIL_GAS("oil_gas"),
    LOGISTICS("logistics"),
    CONSTRUCTION("construction"),
    SERVICES("services");

    private final String value;

    ClientSector(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ClientSector fromValue(String value) {
        return WireEnums.fromValue(ClientSector.class, value);
    }
}
