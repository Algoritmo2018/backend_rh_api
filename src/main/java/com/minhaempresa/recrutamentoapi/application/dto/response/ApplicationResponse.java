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
 * with the application, D8-2), {@code quickProfile} (sent instead of a CV) and {@code clientCvDocumentId}.
 *
 * @param score 0–100; {@code null} = nothing structured to evaluate (anonymous application with a CV only)
 * @param sourceId where the applicant came from ({@code portal}, {@code whatsapp}…)
 * @param clientComments shortlist comments, the oldest first
 * @param clientCvDocumentId the "CV para o cliente" (the only file the client may open), or {@code null}
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
        List<ClientCommentResponse> clientComments,
        String recruiterSummary,
        Instant appliedAt,
        Instant stageEnteredAt,
        Instant updatedAt,
        long version,
        UUID cvDocumentId,
        String cvUrl,
        QuickProfileResponse quickProfile,
        UUID clientCvDocumentId) {

    /** The same application with its shortlist comments (loaded apart, one query per page). */
    public static ApplicationResponse withClientComments(ApplicationResponse r, List<ClientCommentResponse> comments) {
        return new ApplicationResponse(r.id(), r.jobId(), r.candidateId(), r.stage(), r.status(), r.score(), r.matches(), r.eliminated(), r.screeningAnswers(),
                r.rating(), r.tagIds(), r.sourceId(), r.rejectionReasonId(), r.rejectionNote(), r.rejectedAt(), r.rejectedById(), r.sharedWithClient(),
                r.sharedAt(), r.clientDecision(), comments, r.recruiterSummary(), r.appliedAt(), r.stageEnteredAt(), r.updatedAt(), r.version(),
                r.cvDocumentId(), r.cvUrl(), r.quickProfile(), r.clientCvDocumentId());
    }
}
