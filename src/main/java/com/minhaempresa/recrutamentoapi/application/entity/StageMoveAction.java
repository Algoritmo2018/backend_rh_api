package com.minhaempresa.recrutamentoapi.application.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** What happened to an application (frontend {@code StageMoveAction}). */
public enum StageMoveAction implements WireEnum {
    APPLIED("applied"),
    MOVED("moved"),
    REJECTED("rejected"),
    RESTORED("restored"),
    WITHDRAWN("withdrawn");

    private final String value;

    StageMoveAction(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StageMoveAction fromValue(String value) {
        return WireEnums.fromValue(StageMoveAction.class, value);
    }
}
