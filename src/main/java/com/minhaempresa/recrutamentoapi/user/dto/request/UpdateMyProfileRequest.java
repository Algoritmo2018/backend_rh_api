package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Own profile ({@code /app/perfil}). The email can only be changed by an administrator and the phone
 * has its own flow ({@code PUT /me/phone}, password required); a {@code phone} sent here is ignored.
 */
public record UpdateMyProfileRequest(
        @NotBlank(message = "{validation.required}") @PersonName String name,
        @Size(max = 80, message = "{validation.job-title.max}") String jobTitle) {}
