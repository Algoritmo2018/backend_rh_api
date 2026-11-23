package com.minhaempresa.recrutamentoapi.shared.security;

import com.minhaempresa.recrutamentoapi.shared.exception.ApiErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Authenticated but not allowed (filter level) → 403 {@code ForbiddenError} in the standard format. */
@Component
@RequiredArgsConstructor
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN, ApiErrorType.FORBIDDEN_ERROR, "error.forbidden");
    }
}
