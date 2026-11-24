package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestionType;
import java.util.List;
import java.util.UUID;

/** Frontend {@code ScreeningQuestion} (internal: includes the elimination rule). {@code order} starts at 1. */
public record ScreeningQuestionResponse(
        UUID id,
        UUID jobId,
        String question,
        ScreeningQuestionType type,
        List<String> options,
        boolean required,
        boolean eliminatory,
        String expectedAnswer,
        int order) {}
