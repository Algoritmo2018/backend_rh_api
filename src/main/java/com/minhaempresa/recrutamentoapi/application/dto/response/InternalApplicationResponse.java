package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;
import java.util.UUID;

/**
 * Frontend {@code InternalApplication} (applications of the candidate page).
 *
 * @param commentAuthors name of each author of the shortlist comments and whether they belong to the client
 */
public record InternalApplicationResponse(
        @JsonUnwrapped ApplicationResponse application,
        InternalApplicationJobResponse job,
        String stageName,
        String sourceName,
        String rejectionReasonLabel,
        Map<UUID, CommentAuthorResponse> commentAuthors) {}
