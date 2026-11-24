package com.minhaempresa.recrutamentoapi.job.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** State of a job (frontend {@code JobStatus}). Closed jobs cannot be reopened. */
public enum JobStatus implements WireEnum {
    DRAFT("draft"),
    OPEN("open"),
    PAUSED("paused"),
    CLOSED("closed");

    private final String value;

    JobStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Transitions offered by the frontend: publish (draft/paused → open), pause (open → paused) and
     * close (any → closed). A closed job never changes again and nothing goes back to draft. Staying
     * in the same state is always allowed.
     */
    public boolean canChangeTo(JobStatus target) {
        if (target == this) {
            return true;
        }
        return switch (this) {
            case DRAFT, PAUSED -> target == OPEN || target == CLOSED;
            case OPEN -> target == PAUSED || target == CLOSED;
            case CLOSED -> false;
        };
    }

    @JsonCreator
    public static JobStatus fromValue(String value) {
        return WireEnums.fromValue(JobStatus.class, value);
    }
}
