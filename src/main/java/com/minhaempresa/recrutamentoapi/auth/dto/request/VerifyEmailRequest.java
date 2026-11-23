package com.minhaempresa.recrutamentoapi.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Token from the confirmation link ({@code /confirmar-email?token=…}). */
public record VerifyEmailRequest(@NotBlank(message = "{validation.required}") String token) {}
