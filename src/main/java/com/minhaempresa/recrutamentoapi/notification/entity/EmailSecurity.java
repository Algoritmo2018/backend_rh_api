package com.minhaempresa.recrutamentoapi.notification.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Transport security of an SMTP account: none, STARTTLS (usually port 587) or SSL/TLS from the start (465). */
public enum EmailSecurity implements WireEnum {
    NONE("none"),
    STARTTLS("starttls"),
    SSL("ssl");

    private final String value;

    EmailSecurity(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EmailSecurity fromValue(String value) {
        return WireEnums.fromValue(EmailSecurity.class, value);
    }
}
