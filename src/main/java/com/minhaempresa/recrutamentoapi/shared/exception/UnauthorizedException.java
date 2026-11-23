package com.minhaempresa.recrutamentoapi.shared.exception;

import org.springframework.http.HttpStatus;

/** Authentication failed or is missing, e.g. wrong password or invalid OTP (401, {@code AuthError}). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        this("error.unauthorized");
    }

    public UnauthorizedException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public ApiErrorType getErrorType() {
        return ApiErrorType.AUTH_ERROR;
    }
}
