package com.minhaempresa.recrutamentoapi.evaluation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Frontend {@code CompetencyScore}: 0 = not evaluated (left out of the average), 1–5 otherwise. */
public record CompetencyScoreRequest(
        @NotBlank(message = "{validation.required}") @Size(min = 2, max = 60, message = "{validation.evaluation.competency}") String competency,
        @NotNull(message = "{validation.required}") @Min(value = 0, message = "{validation.evaluation.score}") @Max(value = 5, message = "{validation.evaluation.score}")
        Integer score,
        @Size(max = 300, message = "{validation.evaluation.comment}") String comment) {}
