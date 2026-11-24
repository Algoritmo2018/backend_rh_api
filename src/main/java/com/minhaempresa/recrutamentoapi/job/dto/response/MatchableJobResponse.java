package com.minhaempresa.recrutamentoapi.job.dto.response;

import java.util.List;

/**
 * A visible job with its full requirements (weights included) for the match score. Internal to the
 * backend: only the public part ({@code job}) is ever returned to a candidate.
 */
public record MatchableJobResponse(PublicJobResponse job, List<JobRequirementResponse> requirements) {}
