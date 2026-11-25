package com.minhaempresa.recrutamentoapi.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code PATCH /applications/{id}}: absent fields stay.
 *
 * @param tagIds the full list of tags when sent
 * @param recruiterSummary plain text (visible to the client from stage 8c); blank removes it
 */
public record ApplicationUpdateRequest(
        @Min(value = 0, message = "{validation.application.rating}") @Max(value = 5, message = "{validation.application.rating}") Integer rating,
        @Size(max = 20, message = "{validation.application.tags}") List<@NotNull(message = "{validation.required}") UUID> tagIds,
        @Size(max = 2000, message = "{validation.application.summary}") String recruiterSummary) {}
