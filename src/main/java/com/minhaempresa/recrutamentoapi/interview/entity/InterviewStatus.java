package com.minhaempresa.recrutamentoapi.interview.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Status of an interview (frontend {@code InterviewStatus}). */
public enum InterviewStatus implements WireEnum {
    PENDING_SLOT("pending_slot"),
    SCHEDULED("scheduled"),
    DONE("done"),
    NO_SHOW("no_show"),
    CANCELLED("cancelled");

    private final String value;

    InterviewStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    /** Still to happen: waiting for a slot or scheduled (may be changed or cancelled). */
    public boolean isOpen() {
        return this == PENDING_SLOT || this == SCHEDULED;
    }

    @JsonCreator
    public static InterviewStatus fromValue(String value) {
        return WireEnums.fromValue(InterviewStatus.class, value);
    }
}
