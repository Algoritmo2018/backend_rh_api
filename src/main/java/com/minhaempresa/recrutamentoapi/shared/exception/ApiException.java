package com.minhaempresa.recrutamentoapi.shared.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base of the business exceptions. Carries a {@code messages.properties} key (never the text itself)
 * and optional arguments; {@link GlobalExceptionHandler} resolves the message and builds the response.
 */
public abstract class ApiException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;
    private final List<FieldError> fieldErrors;

    protected ApiException(String messageKey, Object... args) {
        this(messageKey, List.of(), args);
    }

    protected ApiException(String messageKey, List<FieldError> fieldErrors, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args.clone();
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    /** Field-level errors (message keys) to return in {@code errors[]}. */
    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public abstract HttpStatus getStatus();

    public abstract ApiErrorType getErrorType();

    /**
     * Field-level business error.
     *
     * @param field name of the field, as in the request JSON
     * @param messageKey key in {@code messages.properties}
     * @param args arguments of the message ({@code {0}}, {@code {1}}…)
     */
    public record FieldError(String field, String messageKey, List<Object> args) {

        public FieldError(String field, String messageKey) {
            this(field, messageKey, List.of());
        }

        public FieldError {
            args = List.copyOf(args);
        }
    }
}
