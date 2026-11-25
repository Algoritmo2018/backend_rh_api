package com.minhaempresa.recrutamentoapi.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /interviews/{id}/status} (frontend {@code setStatus}): {@code done}, {@code no_show} or
 * {@code cancelled}.
 *
 * @param notes replaces the internal notes when sent (e.g. "Cancelada: motivo")
 * @param notifyCandidate JSON {@code notify} ({@code notify} cannot name a record component): tell the candidate of a
 *     cancellation (email with the cancellation {@code .ics})
 */
public record InterviewStatusRequest(
        @NotNull(message = "{validation.required}") InterviewStatus status,
        @Size(max = 1000, message = "{validation.interview.notes}") String notes,
        @JsonProperty("notify") Boolean notifyCandidate,
        @NotNull(message = "{validation.required}") Long version) {}
