package com.minhaempresa.recrutamentoapi.client.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** State of a job request (frontend {@code JobRequestStatus}); {@code APPROVED} exists in the frontend type but no screen sets it. */
public enum JobRequestStatus implements WireEnum {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CONVERTED("converted");

    private final String value;

    JobRequestStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JobRequestStatus fromValue(String value) {
        return WireEnums.fromValue(JobRequestStatus.class, value);
    }
}
