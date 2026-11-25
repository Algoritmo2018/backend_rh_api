package com.minhaempresa.recrutamentoapi.evaluation.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * Scorecard of an application (frontend {@code Evaluation}): one per interviewer of a {@code done} interview, or a
 * free evaluation by an administrator / recruiter ({@code interviewId} null). A submitted evaluation is final until an
 * administrator reopens it (A4).
 */
@Getter
@Entity
@Table(name = "evaluations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation extends UserAuditableEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "interview_id", updatable = false)
    private UUID interviewId;

    @Column(name = "evaluator_id", nullable = false, updatable = false)
    private UUID evaluatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EvaluationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", length = 10)
    private Recommendation recommendation;

    /** Average of the scores above 0, one decimal; {@code null} while nothing was evaluated. */
    @Column(name = "overall_score", precision = 2, scale = 1)
    private BigDecimal overallScore;

    @Column(name = "strengths", length = 800)
    private String strengths;

    @Column(name = "concerns", length = 800)
    private String concerns;

    /** Internal notes (never shown to the client nor the candidate). */
    @Column(name = "notes", length = 1500)
    private String notes;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "evaluation_scores", joinColumns = @JoinColumn(name = "evaluation_id"))
    @OrderColumn(name = "position")
    private List<CompetencyScore> scores = new ArrayList<>();

    private Evaluation(UUID applicationId, UUID candidateId, UUID jobId, UUID interviewId, UUID evaluatorId) {
        this.applicationId = applicationId;
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.interviewId = interviewId;
        this.evaluatorId = evaluatorId;
        this.status = EvaluationStatus.PENDING;
    }

    /** Waiting for an interviewer of a {@code done} interview. */
    public static Evaluation pending(UUID applicationId, UUID candidateId, UUID jobId, UUID interviewId, UUID evaluatorId) {
        return new Evaluation(applicationId, candidateId, jobId, interviewId, evaluatorId);
    }

    /** Free evaluation (no interview), submitted at once with {@link #submit}. */
    public static Evaluation free(UUID applicationId, UUID candidateId, UUID jobId, UUID evaluatorId) {
        return new Evaluation(applicationId, candidateId, jobId, null, evaluatorId);
    }

    /** Fills and submits the scorecard; the average leaves out the competencies scored 0. */
    public void submit(List<CompetencyScore> lines, Recommendation recommendation, String strengths, String concerns, String notes, Instant at) {
        scores.clear();
        scores.addAll(lines);
        this.recommendation = recommendation;
        this.strengths = strengths;
        this.concerns = concerns;
        this.notes = notes;
        this.overallScore = average(lines);
        this.status = EvaluationStatus.SUBMITTED;
        this.submittedAt = at;
    }

    /** A4: back to the evaluator (the previous content stays until they submit again). */
    public void reopen() {
        status = EvaluationStatus.PENDING;
        submittedAt = null;
    }

    /** Erasure (D9-11): scores and recommendation stay, every free text goes. */
    public void eraseTexts() {
        strengths = null;
        concerns = null;
        notes = null;
        List<CompetencyScore> kept = scores.stream().map(CompetencyScore::withoutComment).toList();
        scores.clear();
        scores.addAll(kept);
    }

    public boolean isSubmitted() {
        return status == EvaluationStatus.SUBMITTED;
    }

    /** Average of the scores above 0 (0 = not evaluated), half up to one decimal; {@code null} when none. */
    public static BigDecimal average(List<CompetencyScore> lines) {
        List<Integer> rated = lines.stream().map(l -> (int) l.getScore()).filter(s -> s > 0).toList();
        if (rated.isEmpty()) {
            return null;
        }
        int sum = rated.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(rated.size()), 1, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "Evaluation(id=" + getId() + ", status=" + status + ")";
    }
}
