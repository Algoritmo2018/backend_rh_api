package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Review of a pending job request (frontend {@code review(id, status, {note})}). Only {@code rejected}
 * is accepted here: a request becomes {@code converted} when a job is created from it (stage 5).
 *
 * @param note reason shown to the client (at least 5 characters, like the frontend)
 */
public record JobRequestReviewRequest(
        @NotNull(message = "{validation.required}") JobRequestStatus status,
        @NotBlank(message = "{job-request.note.size}") @Size(min = 5, max = 1000, message = "{job-request.note.size}") String note) {}
