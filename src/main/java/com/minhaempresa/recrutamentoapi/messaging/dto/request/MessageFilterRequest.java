package com.minhaempresa.recrutamentoapi.messaging.dto.request;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageDirection;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Frontend {@code MessageFilters} (query parameters of {@code GET /messages}).
 *
 * @param search text in the subject or body, or part of the candidate's name
 */
public record MessageFilterRequest(
        UUID candidateId,
        UUID applicationId,
        MessageChannel channel,
        MessageDirection direction,
        @Size(max = 100, message = "{validation.invalid-value}") String search) {}
