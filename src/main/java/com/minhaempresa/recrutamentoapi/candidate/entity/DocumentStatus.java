package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Review state of a document (frontend {@code DocumentStatus}; {@code missing} is never stored). */
public enum DocumentStatus implements WireEnum {
    SUBMITTED("submitted"),
    APPROVED("approved"),
    REJECTED("rejected");

    private final String value;

    DocumentStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DocumentStatus fromValue(String value) {
        return WireEnums.fromValue(DocumentStatus.class, value);
    }
}
