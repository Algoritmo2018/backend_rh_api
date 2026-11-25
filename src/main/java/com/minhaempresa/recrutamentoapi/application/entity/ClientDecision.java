package com.minhaempresa.recrutamentoapi.application.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Decision of the client company on a shared application (frontend {@code ClientDecision}, stage 8c/13). */
public enum ClientDecision implements WireEnum {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String value;

    ClientDecision(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ClientDecision fromValue(String value) {
        return WireEnums.fromValue(ClientDecision.class, value);
    }
}
