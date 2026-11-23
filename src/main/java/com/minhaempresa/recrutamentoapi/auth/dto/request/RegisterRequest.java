package com.minhaempresa.recrutamentoapi.auth.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import com.minhaempresa.recrutamentoapi.shared.validation.ValidPassword;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Candidate self-registration (frontend {@code RegisterCandidateInput} plus {@code acceptTerms}, which
 * the frontend must start sending: pending for the integration stage).
 */
public record RegisterRequest(
        @NotBlank(message = "{validation.required}") @PersonName String name,
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @NotNull(message = "{validation.required}") @ValidPassword String password,
        @NotNull(message = "{validation.terms.required}") @AssertTrue(message = "{validation.terms.required}") Boolean acceptTerms) {

    @Override
    public String toString() {
        return "RegisterRequest(email=" + email + ")";
    }
}
