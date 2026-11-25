package com.minhaempresa.recrutamentoapi.application.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Quick profile of the application wizard ("não tenho CV", frontend {@code applySchema.ts}). */
public record QuickProfileRequest(
        @NotBlank(message = "{validation.required}") @Size(min = 3, max = 80, message = "{validation.quick.headline}") String headline,
        @NotEmpty(message = "{validation.quick.skills}") @Size(max = 20, message = "{validation.quick.skills}")
        List<@NotBlank(message = "{validation.required}") @Size(max = 60, message = "{validation.quick.skill}") String> skills,
        @NotNull(message = "{validation.required}") @Min(value = 0, message = "{validation.quick.years}") @Max(value = 50, message = "{validation.quick.years}") Integer years,
        @NotNull(message = "{validation.required}") EducationLevel educationLevel,
        @Size(max = 80, message = "{validation.quick.text}") String lastRole,
        @Size(max = 80, message = "{validation.quick.text}") String lastCompany,
        List<@NotNull(message = "{validation.required}") DriverLicenseCategory> driverLicenses) {}
