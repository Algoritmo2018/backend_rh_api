package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.ReferenceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Result of checking a reference. */
public record ReferenceStatusRequest(
        @NotNull(message = "{validation.required}") ReferenceStatus status,
        @Size(max = 1000, message = "{reference.notes.size}") String notes) {}
