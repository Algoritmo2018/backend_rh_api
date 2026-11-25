package com.minhaempresa.recrutamentoapi.interview.dto.request;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Filters of {@code GET /interviews} (frontend {@code InterviewFilters}). {@code from} inclusive and {@code to}
 * exclusive, on {@code startsAt} (interviews waiting for a slot are left out when a range is given); {@code status}
 * repeated. An interviewer always gets only their own interviews ({@code interviewerId} is forced).
 */
public record InterviewFilterRequest(
        Instant from,
        Instant to,
        UUID interviewerId,
        UUID candidateId,
        UUID applicationId,
        UUID jobId,
        UUID clientId,
        List<InterviewStatus> status) {}
