package com.minhaempresa.recrutamentoapi.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Status of a scorecard: waiting for the evaluator, or submitted (final until an administrator reopens it). */
public enum EvaluationStatus implements WireEnum {
    PENDING("pending"),
    SUBMITTED("submitted");

    private final String value;

    EvaluationStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static EvaluationStatus fromValue(String value) {
        return WireEnums.fromValue(EvaluationStatus.class, value);
    }
}
