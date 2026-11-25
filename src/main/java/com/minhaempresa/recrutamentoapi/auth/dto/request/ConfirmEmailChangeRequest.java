package com.minhaempresa.recrutamentoapi.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Token from the link sent to the new address ({@code /confirmar-novo-email?token=…}). */
public record ConfirmEmailChangeRequest(@NotBlank(message = "{validation.required}") String token) {

    @Override
    public String toString() {
        return "ConfirmEmailChangeRequest";
    }
}
