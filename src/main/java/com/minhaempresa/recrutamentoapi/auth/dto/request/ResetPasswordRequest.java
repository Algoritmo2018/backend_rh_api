package com.minhaempresa.recrutamentoapi.auth.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** New password from the link sent by email ({@code /redefinir-senha?token=…}). */
public record ResetPasswordRequest(
        @NotBlank(message = "{validation.required}") String token,
        @NotNull(message = "{validation.required}") @ValidPassword String password) {

    @Override
    public String toString() {
        return "ResetPasswordRequest";
    }
}
