package com.minhaempresa.recrutamentoapi.evaluation.dto.request;

import com.minhaempresa.recrutamentoapi.evaluation.entity.Recommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Frontend {@code SubmitEvaluationInput}: {@code POST /evaluations/{id}/submit} (pending scorecard) and
 * {@code POST /applications/{id}/evaluations} (free evaluation). At least one score above 0 (422 on {@code scores}).
 */
public record EvaluationSubmitRequest(
        @NotNull(message = "{validation.required}") @Size(max = 20, message = "{validation.evaluation.scores}") List<@NotNull(message = "{validation.required}") @Valid CompetencyScoreRequest> scores,
        @NotNull(message = "{validation.evaluation.recommendation}") Recommendation recommendation,
        @Size(max = 800, message = "{validation.evaluation.strengths}") String strengths,
        @Size(max = 800, message = "{validation.evaluation.concerns}") String concerns,
        @Size(max = 1500, message = "{validation.evaluation.notes}") String notes) {}
