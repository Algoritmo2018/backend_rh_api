package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;
import java.util.UUID;

/**
 * Frontend {@code InternalApplication} (applications of the candidate page).
 *
 * @param commentAuthors authors of the shortlist comments: empty until stage 8c
 */
public record InternalApplicationResponse(
        @JsonUnwrapped ApplicationResponse application,
        InternalApplicationJobResponse job,
        String stageName,
        String sourceName,
        String rejectionReasonLabel,
        Map<UUID, Object> commentAuthors) {}
