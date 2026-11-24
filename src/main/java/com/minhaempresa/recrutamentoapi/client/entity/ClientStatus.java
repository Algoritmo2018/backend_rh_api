package com.minhaempresa.recrutamentoapi.client.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Client companies are never deleted: they are deactivated (their portal users lose access). */
public enum ClientStatus implements WireEnum {
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String value;

    ClientStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ClientStatus fromValue(String value) {
        return WireEnums.fromValue(ClientStatus.class, value);
    }
}
