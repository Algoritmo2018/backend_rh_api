package com.minhaempresa.recrutamentoapi.evaluation.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * A4: an administrator reopened a submitted evaluation. History, never changed, with a copy of the evaluation as it
 * was submitted (audit: administrators and recruiters only). On the candidate's erasure the reason and the free texts
 * of the copy are deleted; the scores, recommendation and average stay.
 */
@Getter
@Entity
@Table(name = "evaluation_reopenings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationReopening extends BaseEntity {

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "reopened_by", updatable = false)
    private UUID reopenedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "reopened_at", nullable = false, updatable = false)
    private Instant reopenedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_recommendation", length = 10, updatable = false)
    private Recommendation previousRecommendation;

    @Column(name = "previous_overall_score", precision = 2, scale = 1, updatable = false)
    private BigDecimal previousOverallScore;

    @Column(name = "previous_strengths", length = 800)
    private String previousStrengths;

    @Column(name = "previous_concerns", length = 800)
    private String previousConcerns;

    @Column(name = "previous_notes", length = 1500)
    private String previousNotes;

    @Column(name = "previous_submitted_at", updatable = false)
    private Instant previousSubmittedAt;

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "evaluation_reopening_scores", joinColumns = @JoinColumn(name = "reopening_id"))
    @OrderColumn(name = "position")
    private List<CompetencyScore> previousScores = new ArrayList<>();

    /** Takes the copy of the submitted evaluation: call it before {@link Evaluation#reopen()}. */
    public EvaluationReopening(Evaluation submitted, UUID reopenedBy, String reason, Instant reopenedAt) {
        this.evaluationId = submitted.getId();
        this.reopenedBy = reopenedBy;
        this.reason = reason;
        this.reopenedAt = reopenedAt;
        this.previousRecommendation = submitted.getRecommendation();
        this.previousOverallScore = submitted.getOverallScore();
        this.previousStrengths = submitted.getStrengths();
        this.previousConcerns = submitted.getConcerns();
        this.previousNotes = submitted.getNotes();
        this.previousSubmittedAt = submitted.getSubmittedAt();
        submitted.getScores().forEach(s -> previousScores.add(new CompetencyScore(s.getCompetency(), s.getScore(), s.getComment())));
    }

    /** Erasure: the reason and every free text of the copy. */
    public void eraseTexts() {
        reason = null;
        previousStrengths = null;
        previousConcerns = null;
        previousNotes = null;
        List<CompetencyScore> kept = previousScores.stream().map(CompetencyScore::withoutComment).toList();
        previousScores.clear();
        previousScores.addAll(kept);
    }
}
