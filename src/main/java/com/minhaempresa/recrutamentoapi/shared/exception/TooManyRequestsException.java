package com.minhaempresa.recrutamentoapi.shared.exception;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** A rate limit was exceeded (429, {@code AuthError}); the response carries a {@code Retry-After} header. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("auth.too-many-requests");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    @Override
    public ApiErrorType getErrorType() {
        return ApiErrorType.AUTH_ERROR;
    }
}
