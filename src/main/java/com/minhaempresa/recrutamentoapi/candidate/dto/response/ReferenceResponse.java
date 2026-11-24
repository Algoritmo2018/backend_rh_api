package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.ReferenceStatus;
import java.time.Instant;
import java.util.UUID;

/** Frontend {@code ReferenceWithRefs}: the reference plus the name of who checked it. */
public record ReferenceResponse(
        UUID id,
        UUID candidateId,
        UUID applicationId,
        String name,
        String company,
        String role,
        String relationship,
        String phone,
        String email,
        ReferenceStatus status,
        String notes,
        UUID checkedById,
        String checkedByName,
        Instant checkedAt,
        Instant createdAt) {}
