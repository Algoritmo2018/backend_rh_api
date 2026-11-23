package com.minhaempresa.recrutamentoapi.settings.dto.request;

import com.minhaempresa.recrutamentoapi.settings.entity.RejectionReasonCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or edit a reason (frontend {@code saveRejectionReason}); {@code active} defaults to true. */
public record RejectionReasonRequest(
        @NotBlank(message = "{settings.reason.label-required}") @Size(max = 120, message = "{settings.reason.label-size}") String label,
        @NotNull(message = "{validation.required}") RejectionReasonCategory category,
        Boolean active) {}
