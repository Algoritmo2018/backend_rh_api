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

/** Education of a candidate (frontend {@code Education}). */
@Getter
@Setter
@Entity
@Table(name = "educations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Education extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "institution", nullable = false, length = 100)
    private String institution;

    @Column(name = "degree", nullable = false, length = 100)
    private String degree;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 10)
    private EducationLevel level;

    @Column(name = "field", length = 100)
    private String field;

    @Column(name = "start_year", nullable = false)
    private int startYear;

    @Column(name = "end_year")
    private Integer endYear;

    Education(Candidate candidate) {
        this.candidate = candidate;
    }
}
