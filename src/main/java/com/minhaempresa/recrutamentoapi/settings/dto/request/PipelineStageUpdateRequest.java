package com.minhaempresa.recrutamentoapi.settings.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** One stage of {@code PUT /settings/stages} (frontend {@code updateStages}: id, name, order, autoAction). */
public record PipelineStageUpdateRequest(
        @NotNull(message = "{validation.required}") UUID id,
        @NotBlank(message = "{settings.stage.name-required}") @Size(max = 40, message = "{settings.stage.name-size}") String name,
        @NotNull(message = "{validation.required}") @Min(value = 1, message = "{settings.stages.order-invalid}") @Max(value = 7, message = "{settings.stages.order-invalid}") Integer order,
        @Valid @NotNull(message = "{validation.required}") StageAutoActionRequest autoAction) {}
