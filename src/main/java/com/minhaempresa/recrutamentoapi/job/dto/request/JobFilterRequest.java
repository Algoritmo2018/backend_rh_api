package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Filters of the internal job list (frontend {@code JobFilters}).
 *
 * @param search part of the title or of the client name (accents and case ignored)
 */
public record JobFilterRequest(
        JobStatus status,
        UUID clientId,
        UUID recruiterId,
        @Size(max = 100, message = "{job.search.size}") String search) {}
