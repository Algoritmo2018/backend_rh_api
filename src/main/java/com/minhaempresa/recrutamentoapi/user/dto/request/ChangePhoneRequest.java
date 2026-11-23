package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Change the own phone number. Always needs the current password; with two-step verification on, also
 * the code sent to the new number by {@code POST /me/phone/code}.
 */
public record ChangePhoneRequest(
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @Pattern(regexp = "\\d{6}", message = "{validation.code.invalid}") String code,
        @NotBlank(message = "{validation.required}") String currentPassword) {

    @Override
    public String toString() {
        return "ChangePhoneRequest";
    }
}
