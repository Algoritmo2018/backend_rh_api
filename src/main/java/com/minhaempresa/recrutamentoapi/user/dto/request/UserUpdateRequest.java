package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Edit a user (frontend {@code UserInput}). The frontend sends the whole object, so {@code email} is
 * accepted but must be equal to the current one (it cannot be changed).
 */
public record UserUpdateRequest(
        @NotBlank(message = "{validation.required}") @PersonName String name,
        String email,
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @NotNull(message = "{validation.required}") UserRole role,
        @Size(max = 80, message = "{validation.job-title.max}") String jobTitle,
        UUID clientId) {}
