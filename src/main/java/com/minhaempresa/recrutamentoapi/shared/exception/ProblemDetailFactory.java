package com.minhaempresa.recrutamentoapi.shared.exception;

import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds every error response of the API, in MVC handlers and in security filters alike:
 *
 * <pre>
 * { "status", "error", "message", "errors": [{ "field", "message" }], "path", "timestamp" }
 * </pre>
 *
 * plus the standard RFC 9457 fields of {@link ProblemDetail} ({@code type}, {@code title},
 * {@code detail} = message, {@code instance} = path).
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

    private final Messages messages;

    public ProblemDetail create(HttpStatusCode status, ApiErrorType type, String message, List<FieldErrorResponse> errors, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved != null) {
            problem.setTitle(resolved.getReasonPhrase());
        }
        problem.setInstance(toUri(path));
        problem.setProperty("error", type.getValue());
        problem.setProperty("message", message);
        problem.setProperty("errors", errors);
        problem.setProperty("path", path);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Same as {@link #create} with the message resolved from {@code messages.properties}. */
    public ProblemDetail fromKey(HttpStatusCode status, ApiErrorType type, String messageKey, List<FieldErrorResponse> errors, String path, Object... args) {
        return create(status, type, messages.get(messageKey, args), errors, path);
    }

    private static URI toUri(String path) {
        try {
            return path == null ? null : URI.create(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
