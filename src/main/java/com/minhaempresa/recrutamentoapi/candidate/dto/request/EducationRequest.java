package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Education of the profile form. An {@code id} that is the UUID of an education of the same candidate
 * updates it; anything else ({@code edu-…}) creates one.
 *
 * @param startYear 1960 to the current year
 * @param endYear empty, or 1960 to the current year + 6 and not before the start
 */
public record EducationRequest(
        String id,
        @NotBlank(message = "{candidate.institution.size}") @Size(min = 2, max = 100, message = "{candidate.institution.size}") String institution,
        @NotBlank(message = "{candidate.degree.size}") @Size(min = 2, max = 100, message = "{candidate.degree.size}") String degree,
        @NotNull(message = "{validation.required}") EducationLevel level,
        @Size(max = 100, message = "{candidate.field.size}") String field,
        @NotNull(message = "{validation.required}") Integer startYear,
        Integer endYear) {}
