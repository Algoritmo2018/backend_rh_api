package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.YearMonth;
import java.util.UUID;

/** Frontend {@code WorkExperience}; dates are {@code "YYYY-MM"}. */
public record WorkExperienceResponse(
        UUID id,
        String company,
        String role,
        Province province,
        YearMonth startDate,
        YearMonth endDate,
        boolean current,
        String description) {}
