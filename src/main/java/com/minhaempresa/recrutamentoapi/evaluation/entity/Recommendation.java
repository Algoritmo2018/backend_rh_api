package com.minhaempresa.recrutamentoapi.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Frontend {@code Recommendation} of a scorecard. */
public enum Recommendation implements WireEnum {
    STRONG_YES("strong_yes"),
    YES("yes"),
    NO("no"),
    STRONG_NO("strong_no");

    private final String value;

    Recommendation(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Recommendation fromValue(String value) {
        return WireEnums.fromValue(Recommendation.class, value);
    }
}
