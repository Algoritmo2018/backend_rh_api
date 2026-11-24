package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import java.util.UUID;

/** Frontend {@code Education}. */
public record EducationResponse(
        UUID id,
        String institution,
        String degree,
        EducationLevel level,
        String field,
        int startYear,
        Integer endYear) {}
