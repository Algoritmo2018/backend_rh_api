package com.minhaempresa.recrutamentoapi.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A candidate asks to change the login email: the link goes to {@code newEmail}. */
public record EmailChangeRequest(
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String newEmail,
        @NotBlank(message = "{validation.required}") String currentPassword) {

    @Override
    public String toString() {
        return "EmailChangeRequest(newEmail=" + newEmail + ")";
    }
}
