package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;

/**
 * Frontend {@code JobListItem}: every field of the job (flattened) plus names and pipeline numbers.
 *
 * @param recruiterActive {@code false} when the responsible recruiter was deactivated (reassign)
 * @param stageCounts candidates per pipeline stage key (0 until stage 8)
 */
public record JobListItemResponse(
        @JsonUnwrapped JobResponse job,
        String clientName,
        String recruiterName,
        boolean recruiterActive,
        Map<String, Long> stageCounts,
        long totalCandidates,
        long activeCandidates,
        long rejectedCount) {}
