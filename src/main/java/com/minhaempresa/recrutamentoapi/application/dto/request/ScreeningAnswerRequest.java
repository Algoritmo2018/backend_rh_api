package com.minhaempresa.recrutamentoapi.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Frontend {@code ScreeningAnswer}: yes/no → {@code sim}/{@code nao}; multiple choice → options joined by
 * {@code |}.
 */
public record ScreeningAnswerRequest(
        @NotNull(message = "{validation.required}") UUID questionId,
        @Size(max = 1000, message = "{validation.answer.max}") String answer) {}
