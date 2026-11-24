package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Order of the public job list (frontend {@code PublicJobQuery.sort}). */
public enum PublicJobSort implements WireEnum {
    RECENT("recent"),
    DEADLINE("deadline"),
    TITLE("title");

    private final String value;

    PublicJobSort(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PublicJobSort fromValue(String value) {
        return WireEnums.fromValue(PublicJobSort.class, value);
    }
}
