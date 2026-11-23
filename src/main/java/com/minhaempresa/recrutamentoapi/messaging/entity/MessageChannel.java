package com.minhaempresa.recrutamentoapi.messaging.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Channel of a message or template (frontend {@code MessageChannel}). */
public enum MessageChannel implements WireEnum {
    EMAIL("email"),
    SMS("sms"),
    WHATSAPP("whatsapp");

    private final String value;

    MessageChannel(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageChannel fromValue(String value) {
        return WireEnums.fromValue(MessageChannel.class, value);
    }
}
