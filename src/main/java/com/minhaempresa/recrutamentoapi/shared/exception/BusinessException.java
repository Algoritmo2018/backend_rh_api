package com.minhaempresa.recrutamentoapi.shared.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A business rule was violated (422, {@code ValidationError}). */
public class BusinessException extends ApiException {

    public BusinessException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    public BusinessException(String messageKey, List<FieldError> fieldErrors, Object... args) {
        super(messageKey, fieldErrors, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }

    @Override
    public ApiErrorType getErrorType() {
        return ApiErrorType.VALIDATION_ERROR;
    }
}
