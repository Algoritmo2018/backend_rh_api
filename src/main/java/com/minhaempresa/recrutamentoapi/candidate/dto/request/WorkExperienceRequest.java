package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;

/**
 * Work experience of the profile form. An {@code id} that is the UUID of an experience of the same
 * candidate updates it; anything else ({@code exp-…}) creates one.
 *
 * @param startDate {@code "YYYY-MM"}, not in the future
 * @param endDate {@code "YYYY-MM"}, required unless {@code current}, not before the start
 */
public record WorkExperienceRequest(
        String id,
        @NotBlank(message = "{candidate.company.size}") @Size(min = 2, max = 80, message = "{candidate.company.size}") String company,
        @NotBlank(message = "{candidate.role.size}") @Size(min = 2, max = 80, message = "{candidate.role.size}") String role,
        Province province,
        @NotNull(message = "{validation.required}") YearMonth startDate,
        YearMonth endDate,
        boolean current,
        @Size(max = 500, message = "{candidate.experience.description.size}") String description) {}
