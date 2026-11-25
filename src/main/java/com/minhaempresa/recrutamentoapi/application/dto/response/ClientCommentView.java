package com.minhaempresa.recrutamentoapi.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A shortlist comment as the client sees it: the author's name and side, never internal ids. */
public record ClientCommentView(UUID id, String authorName, boolean fromClient, String text, Instant createdAt) {}
