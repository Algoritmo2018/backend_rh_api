package com.minhaempresa.recrutamentoapi.shared.security;

import com.minhaempresa.recrutamentoapi.shared.exception.ApiErrorType;
import com.minhaempresa.recrutamentoapi.shared.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the standard error body from servlet filters (Spring Security runs before Spring MVC, so
 * {@code GlobalExceptionHandler} does not see its exceptions).
 */
@Component
@RequiredArgsConstructor
class ProblemResponseWriter {

    private final ProblemDetailFactory problems;
    private final JsonMapper jsonMapper;

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, ApiErrorType type, String messageKey) throws IOException {
        var problem = problems.fromKey(status, type, messageKey, List.of(), request.getRequestURI());
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
