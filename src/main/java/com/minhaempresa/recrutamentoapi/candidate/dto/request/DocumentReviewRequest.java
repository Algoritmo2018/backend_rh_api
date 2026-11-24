package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Review of a document by staff.
 *
 * @param status {@code approved} or {@code rejected}
 * @param note reason shown to the candidate when rejected
 */
public record DocumentReviewRequest(
        @NotNull(message = "{validation.required}") DocumentStatus status,
        @Size(max = 500, message = "{document.note.size}") String note) {}
