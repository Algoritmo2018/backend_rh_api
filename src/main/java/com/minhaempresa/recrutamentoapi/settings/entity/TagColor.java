package com.minhaempresa.recrutamentoapi.settings.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Badge colour of a tag (frontend {@code Tag.color}). */
public enum TagColor implements WireEnum {
    INDIGO("indigo"),
    CYAN("cyan"),
    PURPLE("purple"),
    SUCCESS("success"),
    WARNING("warning"),
    DANGER("danger"),
    MUTED("muted");

    private final String value;

    TagColor(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TagColor fromValue(String value) {
        return WireEnums.fromValue(TagColor.class, value);
    }
}
