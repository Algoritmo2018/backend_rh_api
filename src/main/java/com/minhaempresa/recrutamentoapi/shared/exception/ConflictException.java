package com.minhaempresa.recrutamentoapi.shared.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/** The request conflicts with existing data, e.g. a duplicated email (409, {@code ValidationError}). */
public class ConflictException extends ApiException {

    public ConflictException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public ConflictException(String messageKey, List<FieldError> fieldErrors, Object... args) {
        super(messageKey, fieldErrors, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public ApiErrorType getErrorType() {
        return ApiErrorType.VALIDATION_ERROR;
    }
}
