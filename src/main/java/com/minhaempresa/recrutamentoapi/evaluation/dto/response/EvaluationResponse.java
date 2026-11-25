package com.minhaempresa.recrutamentoapi.evaluation.dto.response;

import com.minhaempresa.recrutamentoapi.evaluation.entity.EvaluationStatus;
import com.minhaempresa.recrutamentoapi.evaluation.entity.Recommendation;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Frontend {@code EvaluationWithRefs}: the scorecard plus the names the screens show.
 *
 * @param interviewId {@code null} for a free evaluation
 * @param overallScore average of the scores above 0 (one decimal); {@code null} while pending
 * @param interview the interview evaluated ({@code null} for a free evaluation)
 * @param reopenings A4 history, newest first (administrators and recruiters only; empty for interviewers)
 */
public record EvaluationResponse(
        UUID id,
        UUID applicationId,
        UUID candidateId,
        UUID jobId,
        UUID interviewId,
        UUID evaluatorId,
        EvaluationStatus status,
        List<Score> scores,
        BigDecimal overallScore,
        Recommendation recommendation,
        String strengths,
        String concerns,
        String notes,
        Instant submittedAt,
        Instant createdAt,
        String candidateName,
        String jobTitle,
        String evaluatorName,
        InterviewRef interview,
        List<Reopening> reopenings) {

    public record Score(String competency, int score, String comment) {}

    public record InterviewRef(UUID id, InterviewType type, Instant startsAt, InterviewMode mode) {}

    /** @param reason {@code null} after the candidate's erasure */
    public record Reopening(String reason, UUID reopenedById, String reopenedByName, Instant reopenedAt) {}
}
