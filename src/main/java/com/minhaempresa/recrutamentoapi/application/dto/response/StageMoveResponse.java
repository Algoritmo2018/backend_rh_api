package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.application.entity.StageMoveAction;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.time.Instant;
import java.util.UUID;

/**
 * Frontend {@code StageMoveWithUser}.
 *
 * @param byUserId {@code null} = the system or the candidate
 */
public record StageMoveResponse(
        UUID id,
        UUID applicationId,
        StageMoveAction action,
        PipelineStageKey fromStage,
        PipelineStageKey toStage,
        UUID byUserId,
        String reason,
        Instant createdAt,
        String byUserName) {}
