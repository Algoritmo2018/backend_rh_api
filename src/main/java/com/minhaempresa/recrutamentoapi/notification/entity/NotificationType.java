package com.minhaempresa.recrutamentoapi.notification.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;

/** Kind of in-app notification (the bell). Later stages add their own kinds. */
public enum NotificationType implements WireEnum {
    JOB_REQUEST_CREATED("job_request_created"),
    JOB_REQUEST_REVIEWED("job_request_reviewed"),
    CONTACT_REQUEST_RECEIVED("contact_request_received"),
    JOB_ASSIGNED("job_assigned"),
    EMAIL_FAILED("email_failed"),
    APPLICATION_RECEIVED("application_received"),
    /** Client users: candidates were shared with their company (stage 8c). */
    CANDIDATES_SHARED("candidates_shared"),
    /** Client users: FEH OIRAM wrote in a shortlist (stage 8c). */
    CLIENT_COMMENT("client_comment");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotificationType fromValue(String value) {
        return WireEnums.fromValue(NotificationType.class, value);
    }
}
