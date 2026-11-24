package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A contact of the full list sent on create/update (frontend {@code ClientContact}). An {@code id} that
 * is the UUID of an existing contact of the same client updates it; any other value (missing, or a
 * frontend id such as {@code cc-…}) creates a new contact.
 */
public record ClientContactRequest(
        String id,
        @NotBlank(message = "{client.contact.name.size}") @Size(min = 3, max = 120, message = "{client.contact.name.size}") String name,
        @NotBlank(message = "{client.contact.role.size}") @Size(min = 2, max = 80, message = "{client.contact.role.size}") String role,
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        boolean isPrimary) {}
