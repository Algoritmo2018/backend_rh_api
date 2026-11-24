package com.minhaempresa.recrutamentoapi.client.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** How soon the client needs the position filled. */
public enum JobRequestUrgency implements WireEnum {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high");

    private final String value;

    JobRequestUrgency(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JobRequestUrgency fromValue(String value) {
        return WireEnums.fromValue(JobRequestUrgency.class, value);
    }
}
