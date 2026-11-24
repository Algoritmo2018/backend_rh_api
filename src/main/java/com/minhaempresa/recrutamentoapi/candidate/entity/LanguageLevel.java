package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Language level (frontend {@code LanguageLevel}), ordered: {@link #rank()} as {@code LANGUAGE_RANK}. */
public enum LanguageLevel implements WireEnum {
    BASIC("basic"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced"),
    FLUENT("fluent"),
    NATIVE("native");

    private final String value;

    LanguageLevel(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    /** Rank of {@code lib/matching.ts} ({@code basic} 1 … {@code native} 5). */
    public int rank() {
        return ordinal() + 1;
    }

    /** Level of a rank (1–5), clamped. */
    public static LanguageLevel ofRank(int rank) {
        return values()[Math.max(1, Math.min(5, rank)) - 1];
    }

    @JsonCreator
    public static LanguageLevel fromValue(String value) {
        return WireEnums.fromValue(LanguageLevel.class, value);
    }
}
