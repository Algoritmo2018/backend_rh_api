package com.minhaempresa.recrutamentoapi.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ask for a password-reset link. The answer never reveals whether the email has an account. */
public record ForgotPasswordRequest(
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email) {}
