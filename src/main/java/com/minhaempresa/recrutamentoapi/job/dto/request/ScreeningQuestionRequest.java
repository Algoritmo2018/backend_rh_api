package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestionType;
import com.minhaempresa.recrutamentoapi.job.validation.ValidScreeningQuestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Screening question of the job form (frontend {@code ScreeningQuestion}; the order is the position in
 * the list). An {@code id} that is the UUID of a question of the same job updates it; anything else
 * creates one.
 *
 * @param options choices of {@code single_choice} / {@code multiple_choice} (at least 2)
 * @param expectedAnswer required when eliminatory: {@code sim}/{@code nao} or one of the options
 */
@ValidScreeningQuestion
public record ScreeningQuestionRequest(
        String id,
        @NotBlank(message = "{job.question.size}") @Size(min = 5, max = 200, message = "{job.question.size}") String question,
        @NotNull(message = "{validation.required}") ScreeningQuestionType type,
        @Size(max = 10, message = "{job.question.options.max}")
        List<@NotBlank(message = "{validation.required}") @Size(max = 100, message = "{job.question.option.size}") String> options,
        boolean required,
        boolean eliminatory,
        @Size(max = 100, message = "{job.question.option.size}") String expectedAnswer) {}
