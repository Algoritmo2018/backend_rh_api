package com.minhaempresa.recrutamentoapi.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One line of a scorecard (frontend {@code CompetencyScore}): 0 = not evaluated, 1–5 otherwise. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompetencyScore {

    @Column(name = "competency", nullable = false, length = 60)
    private String competency;

    @Column(name = "score", nullable = false)
    private short score;

    /** Free text: deleted on erasure. */
    @Column(name = "comment", length = 300)
    private String comment;

    public CompetencyScore(String competency, int score, String comment) {
        this.competency = competency;
        this.score = (short) score;
        this.comment = comment;
    }

    /** The same line without the comment (erasure). */
    public CompetencyScore withoutComment() {
        return new CompetencyScore(competency, score, null);
    }
}
