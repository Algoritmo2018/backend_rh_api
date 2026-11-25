package com.minhaempresa.recrutamentoapi.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Answer to a screening question (frontend {@code ScreeningAnswer}): yes/no as {@code sim}/{@code nao},
 * multiple choice as the options joined by {@code |}. The question text is a copy (it may be corrected later).
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAnswer {

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question", nullable = false, length = 200)
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "text")
    private String answer;

    public ApplicationAnswer(UUID questionId, String question, String answer) {
        this.questionId = questionId;
        this.question = question;
        this.answer = answer;
    }
}
