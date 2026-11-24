package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Seniority of a job (frontend {@code ExperienceLevel}). */
public enum ExperienceLevel implements WireEnum {
    ENTRY("entry"),
    JUNIOR("junior"),
    MID("mid"),
    SENIOR("senior"),
    LEAD("lead");

    private final String value;

    ExperienceLevel(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ExperienceLevel fromValue(String value) {
        return WireEnums.fromValue(ExperienceLevel.class, value);
    }
}
