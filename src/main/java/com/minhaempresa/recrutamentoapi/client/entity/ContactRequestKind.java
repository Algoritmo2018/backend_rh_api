package com.minhaempresa.recrutamentoapi.client.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Which public form was used: "Precisa de recrutar?" (company) or Contacto (general). */
public enum ContactRequestKind implements WireEnum {
    COMPANY("company"),
    GENERAL("general");

    private final String value;

    ContactRequestKind(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ContactRequestKind fromValue(String value) {
        return WireEnums.fromValue(ContactRequestKind.class, value);
    }
}
