package com.minhaempresa.recrutamentoapi.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Second step of a password login for accounts with two-step verification. */
public record TwoFactorLoginRequest(
        @NotBlank(message = "{validation.required}") String challengeToken,
        @NotBlank(message = "{validation.required}") @Pattern(regexp = "\\d{6}", message = "{validation.code.invalid}") String code) {}
