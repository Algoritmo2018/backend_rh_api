package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * New professional reference (frontend {@code ReferenceInput}, rules of {@code refSchema}).
 *
 * @param applicationId must be {@code null} until the applications exist (stage 8)
 * @param email optional ({@code ""} = none)
 */
public record ReferenceCreateRequest(
        @NotNull(message = "{validation.required}") UUID candidateId,
        UUID applicationId,
        @NotBlank(message = "{validation.name.invalid}") @PersonName String name,
        @NotBlank(message = "{reference.company.size}") @Size(min = 2, max = 80, message = "{reference.company.size}") String company,
        @NotBlank(message = "{reference.role.size}") @Size(min = 2, max = 80, message = "{reference.role.size}") String role,
        @NotBlank(message = "{reference.relationship.size}") @Size(min = 2, max = 60, message = "{reference.relationship.size}") String relationship,
        @NotBlank(message = "{validation.phone.invalid}") @AngolanPhone String phone,
        @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email) {}
