package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.minhaempresa.recrutamentoapi.job.entity.RequirementCategory;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementType;
import java.util.UUID;

/** Frontend {@code JobRequirement}. */
public record JobRequirementResponse(
        UUID id,
        UUID jobId,
        String description,
        RequirementType type,
        int weight,
        RequirementCategory category,
        String value,
        Integer minValue) {}
