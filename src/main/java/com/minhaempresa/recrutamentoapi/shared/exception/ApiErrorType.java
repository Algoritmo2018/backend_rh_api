package com.minhaempresa.recrutamentoapi.shared.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Value of the {@code error} field of an error response. The names match the error classes of the
 * frontend ({@code NotFoundError}, {@code ForbiddenError}, {@code ValidationError}, {@code AuthError}),
 * so the frontend HTTP client can recreate them.
 */
public enum ApiErrorType {
    VALIDATION_ERROR("ValidationError"),
    AUTH_ERROR("AuthError"),
    FORBIDDEN_ERROR("ForbiddenError"),
    NOT_FOUND_ERROR("NotFoundError"),
    INTERNAL_ERROR("InternalError");

    private final String value;

    ApiErrorType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** Error type for an HTTP status raised by the framework (not by our own exceptions). */
    public static ApiErrorType forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> AUTH_ERROR;
            case 403 -> FORBIDDEN_ERROR;
            case 404 -> NOT_FOUND_ERROR;
            default -> status.is5xxServerError() ? INTERNAL_ERROR : VALIDATION_ERROR;
        };
    }
}
