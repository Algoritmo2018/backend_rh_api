package com.minhaempresa.recrutamentoapi.application.dto.request;

import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /applications/bulk-move}. Each application goes with the version read with it (the frontend sends
 * {@code ids[]} today: integration pending item). At most {@code app.applications.bulk.max-items} (422).
 */
public record ApplicationBulkMoveRequest(
        @NotEmpty(message = "{validation.application.ids}") List<@Valid @NotNull(message = "{validation.required}") ApplicationVersionRef> items,
        @NotNull(message = "{validation.required}") PipelineStageKey toStage,
        @Size(max = 500, message = "{validation.application.note}") String note,
        Boolean runAutoAction,
        UUID templateId) {}
