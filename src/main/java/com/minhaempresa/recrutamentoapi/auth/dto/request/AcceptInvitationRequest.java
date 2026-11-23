package com.minhaempresa.recrutamentoapi.auth.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** First password of an invited user ({@code /aceitar-convite?token=…}). */
public record AcceptInvitationRequest(
        @NotBlank(message = "{validation.required}") String token,
        @NotNull(message = "{validation.required}") @ValidPassword String password) {

    @Override
    public String toString() {
        return "AcceptInvitationRequest";
    }
}
