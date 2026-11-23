package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Field names as the frontend {@code changePassword(id, current, next)}. */
public record ChangePasswordRequest(
        @NotBlank(message = "{validation.required}") String current,
        @NotNull(message = "{validation.required}") @ValidPassword String next) {}
