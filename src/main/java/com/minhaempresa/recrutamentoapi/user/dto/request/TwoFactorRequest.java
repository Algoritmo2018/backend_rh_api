package com.minhaempresa.recrutamentoapi.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Enable two-step verification with the SMS code from {@code POST /me/two-factor/code}, or disable it
 * with the current password. Extends the frontend {@code setTwoFactor(id, enabled, code?)} with
 * {@code currentPassword} (pending for the integration stage).
 */
public record TwoFactorRequest(
        @NotNull(message = "{validation.required}") Boolean enabled,
        @Pattern(regexp = "\\d{6}", message = "{validation.code.invalid}") String code,
        String currentPassword) {

    @Override
    public String toString() {
        return "TwoFactorRequest(enabled=" + enabled + ")";
    }
}
