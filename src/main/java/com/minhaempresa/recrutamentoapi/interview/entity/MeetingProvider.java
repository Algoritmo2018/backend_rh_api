package com.minhaempresa.recrutamentoapi.interview.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Video meeting service (frontend {@code meetingProvider}); its link must be on its own host (A3). */
public enum MeetingProvider implements WireEnum {
    MEET("meet"),
    TEAMS("teams");

    private final String value;

    MeetingProvider(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MeetingProvider fromValue(String value) {
        return WireEnums.fromValue(MeetingProvider.class, value);
    }
}
