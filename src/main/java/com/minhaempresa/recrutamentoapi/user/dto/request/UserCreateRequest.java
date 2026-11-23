package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** New staff or client-portal user (frontend {@code UserInput}); receives an invitation email. */
public record UserCreateRequest(
        @NotBlank(message = "{validation.required}") @PersonName String name,
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @NotNull(message = "{validation.required}") UserRole role,
        @Size(max = 80, message = "{validation.job-title.max}") String jobTitle,
        UUID clientId) {}
