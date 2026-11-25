package com.minhaempresa.recrutamentoapi.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Frontend {@code ClientComment} (staff views; names of the authors in {@code commentAuthors}). */
public record ClientCommentResponse(UUID id, UUID authorId, String text, Instant createdAt) {}
