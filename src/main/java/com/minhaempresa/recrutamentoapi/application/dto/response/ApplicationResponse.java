package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus;
import com.minhaempresa.recrutamentoapi.application.entity.ClientDecision;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse.RequirementMatch;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Frontend {@code Application} (staff views), plus {@code version}, {@code cvDocumentId}/{@code cvUrl} (the CV sent
 * with the application, D8-2) and {@code quickProfile} (sent instead of a CV).
 *
 * @param score 0–100; {@code null} = nothing structured to evaluate (anonymous application with a CV only)
 * @param sourceId where the applicant came from ({@code portal}, {@code whatsapp}…)
 * @param clientComments shortlist comments: empty until stage 8c
 * @param version send it back when moving (stale → 409)
 */
public record ApplicationResponse(
        UUID id,
        UUID jobId,
        UUID candidateId,
        PipelineStageKey stage,
        ApplicationStatus status,
        Integer score,
        List<RequirementMatch> matches,
        boolean eliminated,
        List<ScreeningAnswerResponse> screeningAnswers,
        int rating,
        Set<UUID> tagIds,
        CandidateSource sourceId,
        UUID rejectionReasonId,
        String rejectionNote,
        Instant rejectedAt,
        UUID rejectedById,
        boolean sharedWithClient,
        Instant sharedAt,
        ClientDecision clientDecision,
        List<Object> clientComments,
        String recruiterSummary,
        Instant appliedAt,
        Instant stageEnteredAt,
        Instant updatedAt,
        long version,
        UUID cvDocumentId,
        String cvUrl,
        QuickProfileResponse quickProfile) {}
