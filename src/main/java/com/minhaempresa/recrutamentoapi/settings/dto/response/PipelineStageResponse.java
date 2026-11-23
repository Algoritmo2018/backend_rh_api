package com.minhaempresa.recrutamentoapi.settings.dto.response;

import com.minhaempresa.recrutamentoapi.settings.entity.StageColor;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.util.UUID;

/** Frontend {@code PipelineStage} ({@code order} is the position, 1 to 7). */
public record PipelineStageResponse(UUID id, PipelineStageKey key, String name, int order, StageColor color, StageAutoActionResponse autoAction) {}
