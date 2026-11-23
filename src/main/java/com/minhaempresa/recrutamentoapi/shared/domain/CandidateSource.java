package com.minhaempresa.recrutamentoapi.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Where a candidate came from (frontend {@code Source}; fixed list, no admin screen). The wire value
 * replaces the frontend ids ({@code src-portal} → {@code portal}); the display name is in
 * {@code messages.properties} ({@code source.<value>}).
 */
public enum CandidateSource implements WireEnum {
    PORTAL("portal", "portal"),
    LINKEDIN("linkedin", "social"),
    FACEBOOK("facebook", "social"),
    WHATSAPP("whatsapp", "social"),
    JOB_BOARD("job_board", "job_board"),
    REFERRAL("referral", "referral"),
    TALENT_POOL("talent_pool", "talent_pool"),
    SPONTANEOUS("spontaneous", "spontaneous");

    private final String value;
    private final String type;

    CandidateSource(String value, String type) {
        this.value = value;
        this.type = type;
    }

    @Override
    public String getValue() {
        return value;
    }

    /** Group of the frontend {@code SourceType} ({@code social}, {@code portal}…). */
    public String type() {
        return type;
    }

    /** Message key of the display name. */
    public String messageKey() {
        return "source." + value;
    }

    @JsonCreator
    public static CandidateSource fromValue(String value) {
        return WireEnums.fromValue(CandidateSource.class, value);
    }
}
