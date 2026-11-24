package com.minhaempresa.recrutamentoapi.shared.exception;

import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * Turns every exception raised while handling a request into the standard error response (see
 * {@link ProblemDetailFactory}). Stack traces are logged, never returned.
 *
 * <ul>
 *   <li>Bean Validation / binding / type errors → 400 {@code ValidationError} with {@code errors[]}
 *   <li>{@link ApiException} subclasses → their own status and type
 *   <li>Spring Security exceptions thrown inside controllers → 401 / 403
 *   <li>anything else → 500 {@code InternalError}
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetailFactory problems;
    private final Messages messages;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getFieldErrors().stream()
                .map(e -> new FieldErrorResponse(e.field(), messages.get(e.messageKey(), e.args().toArray())))
                .toList();
        ProblemDetail body = problems.fromKey(ex.getStatus(), ex.getErrorType(), ex.getMessageKey(), errors, pathOf(request), ex.getArgs());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
        if (ex instanceof TooManyRequestsException tooMany) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, tooMany.getRetryAfter().toSeconds())));
        }
        return response.body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(HttpStatus.FORBIDDEN, ApiErrorType.FORBIDDEN_ERROR, "error.forbidden", List.of(), request);
    }

    /**
     * Someone else saved the same record in between (JPA {@code @Version}): 409, the client must reload.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        return respond(HttpStatus.CONFLICT, ApiErrorType.VALIDATION_ERROR, "error.concurrent-update", List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ApiErrorType.AUTH_ERROR, "error.unauthorized", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", pathOf(request), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorType.INTERNAL_ERROR, "error.internal", List.of(), request);
    }

    /**
     * Single exit point for the Spring MVC exceptions handled by {@link ResponseEntityExceptionHandler}
     * (validation, malformed body, unsupported method, missing resource…): replaces the default body.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = fieldErrorsOf(ex);
        String messageKey = !errors.isEmpty() ? "error.validation" : messageKeyFor(status);
        if (status.is5xxServerError()) {
            log.error("Request failed on {}", pathOf(request), ex);
        }
        ProblemDetail problem = problems.fromKey(status, ApiErrorType.forStatus(status), messageKey, errors, pathOf(request));
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, ApiErrorType type, String messageKey, List<FieldErrorResponse> errors, WebRequest request) {
        return ResponseEntity.status(status).body(problems.fromKey(status, type, messageKey, errors, pathOf(request)));
    }

    private List<FieldErrorResponse> fieldErrorsOf(Exception ex) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (ex instanceof BindingResult bindingResult) {
            // MethodArgumentNotValidException (@Valid @RequestBody / @ModelAttribute)
            addBindingErrors(bindingResult, errors);
        } else if (ex instanceof HandlerMethodValidationException validation) {
            // @Valid / constraints directly on controller parameters
            validation.getParameterValidationResults().forEach(result -> {
                if (result instanceof ParameterErrors parameterErrors) {
                    addBindingErrors(parameterErrors, errors);
                } else {
                    String name = result.getMethodParameter().getParameterName();
                    for (MessageSourceResolvable error : result.getResolvableErrors()) {
                        errors.add(new FieldErrorResponse(name, error.getDefaultMessage()));
                    }
                }
            });
        } else if (ex instanceof TypeMismatchException mismatch) {
            errors.add(new FieldErrorResponse(mismatch.getPropertyName(), invalidValueMessage()));
        } else if (ex instanceof HttpMessageNotReadableException && ex.getCause() instanceof JacksonException jackson) {
            String field = jsonPath(jackson);
            if (!field.isEmpty()) {
                errors.add(new FieldErrorResponse(field, invalidValueMessage()));
            }
        }
        return errors;
    }

    private void addBindingErrors(Errors source, List<FieldErrorResponse> errors) {
        for (FieldError error : source.getFieldErrors()) {
            String message = error.isBindingFailure() ? invalidValueMessage() : error.getDefaultMessage();
            errors.add(new FieldErrorResponse(error.getField(), message));
        }
        source.getGlobalErrors().forEach(error -> errors.add(new FieldErrorResponse(error.getObjectName(), error.getDefaultMessage())));
    }

    private String invalidValueMessage() {
        return messages.get("validation.invalid-value");
    }

    /** {@code requirements[0].value} from the Jackson path of a deserialization error. */
    private static String jsonPath(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String messageKeyFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "error.bad-request";
            case 401 -> "error.unauthorized";
            case 403 -> "error.forbidden";
            case 404 -> "error.not-found";
            case 405 -> "error.method-not-allowed";
            case 406 -> "error.not-acceptable";
            case 409 -> "error.conflict";
            case 413 -> "error.payload-too-large";
            case 415 -> "error.unsupported-media-type";
            default -> status.is5xxServerError() ? "error.internal" : "error.request";
        };
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
