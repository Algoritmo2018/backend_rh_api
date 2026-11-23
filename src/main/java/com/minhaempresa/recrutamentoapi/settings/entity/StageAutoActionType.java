package com.minhaempresa.recrutamentoapi.settings.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Automatic action when an application enters a stage (frontend {@code StageAutoAction.type}). */
public enum StageAutoActionType implements WireEnum {
    NONE("none"),
    SEND_MESSAGE("send_message");

    private final String value;

    StageAutoActionType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StageAutoActionType fromValue(String value) {
        return WireEnums.fromValue(StageAutoActionType.class, value);
    }
}
