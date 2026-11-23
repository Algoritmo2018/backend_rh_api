package com.minhaempresa.recrutamentoapi.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email + password login (frontend {@code LoginWithEmailInput}). */
public record LoginRequest(
        @NotBlank(message = "{validation.required}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.required}") @Size(max = 200, message = "{validation.password.invalid}") String password) {

    @Override
    public String toString() {
        return "LoginRequest(email=" + email + ")";
    }
}
