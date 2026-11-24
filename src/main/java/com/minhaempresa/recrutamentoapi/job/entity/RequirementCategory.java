package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** What a requirement is compared with in the candidate profile (frontend {@code RequirementCategory}). */
public enum RequirementCategory implements WireEnum {
    SKILL("skill"),
    EXPERIENCE("experience"),
    EDUCATION("education"),
    LANGUAGE("language"),
    LICENSE("license"),
    LOCATION("location");

    private final String value;

    RequirementCategory(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RequirementCategory fromValue(String value) {
        return WireEnums.fromValue(RequirementCategory.class, value);
    }
}
