package com.minhaempresa.recrutamentoapi.client.dto.response;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequestUrgency;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Frontend {@code JobRequestWithRefs} ({@code JobRequest} plus the client and requester names).
 *
 * @param desiredStartDate {@code "YYYY-MM-DD"}
 */
public record JobRequestResponse(
        UUID id,
        UUID clientId,
        UUID requestedById,
        String title,
        JobArea area,
        Province province,
        int vacancies,
        String description,
        String requirements,
        Money salaryBudget,
        LocalDate desiredStartDate,
        JobRequestUrgency urgency,
        JobRequestStatus status,
        UUID jobId,
        String reviewNote,
        Instant createdAt,
        String clientName,
        String requestedByName) {}
