package com.minhaempresa.recrutamentoapi.client.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Follow-up of a public contact request by the administrators. */
public enum ContactRequestStatus implements WireEnum {
    NEW("new"),
    CONTACTED("contacted"),
    CLOSED("closed");

    private final String value;

    ContactRequestStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ContactRequestStatus fromValue(String value) {
        return WireEnums.fromValue(ContactRequestStatus.class, value);
    }
}
