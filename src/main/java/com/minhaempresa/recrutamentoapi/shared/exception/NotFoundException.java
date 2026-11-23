package com.minhaempresa.recrutamentoapi.shared.exception;

import org.springframework.http.HttpStatus;

/** The requested record does not exist (404, {@code NotFoundError}). Example key: {@code job.not-found}. */
public class NotFoundException extends ApiException {

    public NotFoundException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public ApiErrorType getErrorType() {
        return ApiErrorType.NOT_FOUND_ERROR;
    }
}
