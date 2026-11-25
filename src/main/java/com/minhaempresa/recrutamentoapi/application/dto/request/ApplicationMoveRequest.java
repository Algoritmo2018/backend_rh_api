package com.minhaempresa.recrutamentoapi.application.dto.request;

import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code POST /applications/{id}/move} (frontend {@code MoveOptions}; the user comes from the token).
 *
 * @param runAutoAction send the automatic message of the target stage (or {@code templateId})
 * @param templateId replaces the template of the stage's automatic action
 * @param version the version read with the application: someone else changed it meanwhile → 409
 */
public record ApplicationMoveRequest(
        @NotNull(message = "{validation.required}") PipelineStageKey toStage,
        @Size(max = 500, message = "{validation.application.note}") String note,
        Boolean runAutoAction,
        UUID templateId,
        @NotNull(message = "{validation.required}") Long version) {}
