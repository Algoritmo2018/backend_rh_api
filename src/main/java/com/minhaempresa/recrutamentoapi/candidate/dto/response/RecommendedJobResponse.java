package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.minhaempresa.recrutamentoapi.job.dto.response.PublicJobResponse;

/**
 * Frontend {@code RecommendedJob} ({@code PublicJob & {match}}).
 *
 * @param match match score of the candidate for this job (0–100)
 */
public record RecommendedJobResponse(@JsonUnwrapped PublicJobResponse job, int match) {}
