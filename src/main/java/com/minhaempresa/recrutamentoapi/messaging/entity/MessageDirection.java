package com.minhaempresa.recrutamentoapi.messaging.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Direction of a message (frontend {@code Message.direction}); inbound messages do not exist yet. */
public enum MessageDirection implements WireEnum {
    OUTBOUND("outbound"),
    INBOUND("inbound");

    private final String value;

    MessageDirection(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageDirection fromValue(String value) {
        return WireEnums.fromValue(MessageDirection.class, value);
    }
}
