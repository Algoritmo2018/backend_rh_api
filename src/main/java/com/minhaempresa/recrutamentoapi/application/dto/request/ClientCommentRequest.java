package com.minhaempresa.recrutamentoapi.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /applications/{id}/client-comments}: a FEH OIRAM comment in the shortlist (plain text). */
public record ClientCommentRequest(
        @NotBlank(message = "{validation.application.comment}") @Size(min = 2, max = 1000, message = "{validation.application.comment}") String text) {}
