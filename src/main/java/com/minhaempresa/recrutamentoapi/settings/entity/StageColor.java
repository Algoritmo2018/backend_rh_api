package com.minhaempresa.recrutamentoapi.settings.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Colour of a pipeline stage column (frontend {@code PipelineStage.color}); fixed per stage. */
public enum StageColor implements WireEnum {
    SLATE("slate"),
    CYAN("cyan"),
    BLUE("blue"),
    INDIGO("indigo"),
    PURPLE("purple"),
    WARNING("warning"),
    SUCCESS("success");

    private final String value;

    StageColor(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static StageColor fromValue(String value) {
        return WireEnums.fromValue(StageColor.class, value);
    }
}
