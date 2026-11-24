package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Education level (frontend {@code EducationLevel}), ordered: {@link #rank()} as {@code EDUCATION_RANK}. */
public enum EducationLevel implements WireEnum {
    BASIC("basic"),
    SECONDARY("secondary"),
    TECHNICAL("technical"),
    BACHELOR("bachelor"),
    LICENTIATE("licentiate"),
    MASTER("master"),
    PHD("phd");

    private final String value;

    EducationLevel(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    /** Rank of {@code lib/matching.ts} ({@code basic} 0 … {@code phd} 6). */
    public int rank() {
        return ordinal();
    }

    @JsonCreator
    public static EducationLevel fromValue(String value) {
        return WireEnums.fromValue(EducationLevel.class, value);
    }
}
