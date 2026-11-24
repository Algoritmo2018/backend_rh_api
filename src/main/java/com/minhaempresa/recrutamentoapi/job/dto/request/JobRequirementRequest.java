package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.RequirementCategory;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementType;
import com.minhaempresa.recrutamentoapi.job.validation.ValidJobRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Requirement of the job form (frontend {@code JobRequirement}). An {@code id} that is the UUID of a
 * requirement of the same job updates it; anything else creates one.
 *
 * @param value required except for {@code experience}
 * @param minValue minimum years ({@code experience}) or level ({@code language}); ignored otherwise
 */
@ValidJobRequirement
public record JobRequirementRequest(
        String id,
        @NotBlank(message = "{job.requirement.description.size}") @Size(min = 3, max = 160, message = "{job.requirement.description.size}") String description,
        @NotNull(message = "{validation.required}") RequirementType type,
        @NotNull(message = "{validation.required}") @Min(value = 1, message = "{job.requirement.weight.range}") @Max(value = 5, message = "{job.requirement.weight.range}") Integer weight,
        @NotNull(message = "{validation.required}") RequirementCategory category,
        @Size(max = 120, message = "{job.requirement.value.size}") String value,
        @Min(value = 0, message = "{job.requirement.min-value.range}") @Max(value = 40, message = "{job.requirement.min-value.range}") Integer minValue) {}
