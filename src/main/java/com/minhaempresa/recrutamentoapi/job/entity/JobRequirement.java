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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Requirement of a job, compared with each candidate profile to compute the match score. Once a job has
 * applications, {@code weight} and {@code minValue} can no longer change (stage 8).
 */
@Getter
@Setter
@Entity
@Table(name = "job_requirements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirement extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private Job job;

    /** Order in the form (0-based). */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", nullable = false, length = 160)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private RequirementType type;

    /** 1 to 5. */
    @Column(name = "weight", nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private RequirementCategory category;

    /** Compared with the profile; {@code null} only for {@code EXPERIENCE}. */
    @Column(name = "value", length = 120)
    private String value;

    /** Minimum years (experience) or level (language). */
    @Column(name = "min_value")
    private Integer minValue;

    JobRequirement(Job job) {
        this.job = job;
    }
}
