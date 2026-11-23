package com.minhaempresa.recrutamentoapi.shared.exception;

import org.springframework.http.HttpStatus;

/** The user is authenticated but may not access this record (403, {@code ForbiddenError}). */
public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        this("error.forbidden");
    }

    public ForbiddenException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    @Override
    public ApiErrorType getErrorType() {
        return ApiErrorType.FORBIDDEN_ERROR;
    }
}
