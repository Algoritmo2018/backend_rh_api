package com.minhaempresa.recrutamentoapi.job.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Screening question of a job's application form. {@code eliminatory} and {@code expectedAnswer} are
 * never shown to candidates. Once a job has applications, a question cannot be deleted nor change its
 * type, options or elimination rule (stage 8); the text may still be corrected.
 */
@Getter
@Setter
@Entity
@Table(name = "screening_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreeningQuestion extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private Job job;

    /** Order in the form (0-based); the API exposes {@code order} starting at 1. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "question", nullable = false, length = 200)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ScreeningQuestionType type;

    /** Choices of {@code SINGLE_CHOICE} / {@code MULTIPLE_CHOICE} questions (empty otherwise). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "options", nullable = false, columnDefinition = "text[]")
    private List<String> options = new ArrayList<>();

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "eliminatory", nullable = false)
    private boolean eliminatory;

    /** {@code sim}/{@code nao} for yes/no questions, or one of the options. */
    @Column(name = "expected_answer", length = 100)
    private String expectedAnswer;

    ScreeningQuestion(Job job) {
        this.job = job;
    }
}
