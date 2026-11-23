package com.minhaempresa.recrutamentoapi.settings.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Who originated a rejection (frontend {@code RejectionReason.category}). */
public enum RejectionReasonCategory implements WireEnum {
    COMPANY("company"),
    CANDIDATE("candidate"),
    CLIENT("client");

    private final String value;

    RejectionReasonCategory(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RejectionReasonCategory fromValue(String value) {
        return WireEnums.fromValue(RejectionReasonCategory.class, value);
    }
}
