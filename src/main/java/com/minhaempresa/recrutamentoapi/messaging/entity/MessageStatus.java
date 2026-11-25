package com.minhaempresa.recrutamentoapi.messaging.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Delivery status of a message (frontend {@code MessageStatus}). Outbound email: {@code queued} until the outbox delivers it. */
public enum MessageStatus implements WireEnum {
    QUEUED("queued"),
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read"),
    FAILED("failed");

    private final String value;

    MessageStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageStatus fromValue(String value) {
        return WireEnums.fromValue(MessageStatus.class, value);
    }
}
