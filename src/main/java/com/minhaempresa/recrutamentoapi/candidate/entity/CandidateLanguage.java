package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Language of a candidate (frontend {@code LanguageSkill}, which has no id). */
@Getter
@Setter
@Entity
@Table(name = "candidate_languages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateLanguage extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "language", nullable = false, length = 30)
    private String language;

    /** Lower case without accents: comparisons with job requirements and uniqueness. */
    @Column(name = "language_key", nullable = false, length = 30)
    private String languageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 12)
    private LanguageLevel level;

    CandidateLanguage(Candidate candidate, String language, String languageKey, LanguageLevel level) {
        this.candidate = candidate;
        this.language = language;
        this.languageKey = languageKey;
        this.level = level;
    }
}
