package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Check state of a professional reference (frontend {@code ReferenceStatus}). */
public enum ReferenceStatus implements WireEnum {
    PENDING("pending"),
    CONTACTED("contacted"),
    VERIFIED("verified"),
    UNREACHABLE("unreachable"),
    NEGATIVE("negative");

    private final String value;

    ReferenceStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReferenceStatus fromValue(String value) {
        return WireEnums.fromValue(ReferenceStatus.class, value);
    }
}
