package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Match between a candidate and the requirements of a job (DECISOES.md #20), with the reason of each
 * requirement so it can be explained.
 *
 * @param score 0–100
 */
public record MatchResultResponse(int score, List<RequirementMatch> matches) {

    /** Frontend {@code RequirementMatch}: {@code detail} is the explanation shown to staff. */
    public record RequirementMatch(UUID requirementId, boolean met, String detail) {}
}
