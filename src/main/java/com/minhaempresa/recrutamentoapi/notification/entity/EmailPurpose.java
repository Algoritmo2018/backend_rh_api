package com.minhaempresa.recrutamentoapi.notification.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Kind of email, to choose the sending account: system notices (noreply), recruitment (candidates), clients. */
public enum EmailPurpose implements WireEnum {
    SYSTEM("system"),
    RECRUITMENT("recruitment"),
    CLIENTS("clients");

    private final String value;

    EmailPurpose(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EmailPurpose fromValue(String value) {
        return WireEnums.fromValue(EmailPurpose.class, value);
    }
}
