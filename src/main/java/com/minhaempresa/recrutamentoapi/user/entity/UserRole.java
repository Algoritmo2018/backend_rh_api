package com.minhaempresa.recrutamentoapi.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnum;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;
import java.util.Set;

/**
 * Profile of a user account. The JWT {@code roles} claim carries the constant name ({@code ADMIN}),
 * which becomes the authority {@code ROLE_ADMIN}.
 */
public enum UserRole implements WireEnum {
    CANDIDATE("candidate"),
    ADMIN("admin"),
    RECRUITER("recruiter"),
    INTERVIEWER("interviewer"),
    CLIENT("client");

    /** FEH OIRAM staff (internal panel {@code /app}). */
    public static final Set<UserRole> INTERNAL = Set.of(ADMIN, RECRUITER, INTERVIEWER);

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UserRole fromValue(String value) {
        return WireEnums.fromValue(UserRole.class, value);
    }
}
