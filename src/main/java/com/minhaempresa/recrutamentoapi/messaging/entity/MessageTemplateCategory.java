package com.minhaempresa.recrutamentoapi.messaging.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Purpose of a message template (frontend {@code MessageTemplateCategory}). */
public enum MessageTemplateCategory implements WireEnum {
    APPLICATION_RECEIVED("application_received"),
    INTERVIEW_INVITE("interview_invite"),
    INTERVIEW_REMINDER("interview_reminder"),
    TEST_INVITE("test_invite"),
    REJECTION("rejection"),
    OFFER("offer"),
    DOCUMENTS_REQUEST("documents_request"),
    GENERAL("general");

    private final String value;

    MessageTemplateCategory(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageTemplateCategory fromValue(String value) {
        return WireEnums.fromValue(MessageTemplateCategory.class, value);
    }
}
