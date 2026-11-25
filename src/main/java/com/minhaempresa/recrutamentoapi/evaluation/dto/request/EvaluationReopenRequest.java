package com.minhaempresa.recrutamentoapi.evaluation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A4: {@code POST /evaluations/{id}/reopen} (administrators): why the evaluation goes back to the evaluator. */
public record EvaluationReopenRequest(
        @NotBlank(message = "{validation.required}") @Size(min = 5, max = 500, message = "{validation.evaluation.reopen-reason}") String reason) {}
