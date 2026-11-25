package com.minhaempresa.recrutamentoapi.application.dto.response;

import java.util.UUID;

/**
 * Frontend {@code ScreeningAnswer} plus the question text as it was when applying.
 *
 * @param answer yes/no {@code sim}/{@code nao}; multiple choice joined by {@code |}
 */
public record ScreeningAnswerResponse(UUID questionId, String question, String answer) {}
