package com.minhaempresa.recrutamentoapi.candidate.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.persistence.UserAuditableEntity;
import com.minhaempresa.recrutamentoapi.shared.persistence.YearMonthDateConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Work experience of a candidate (frontend {@code WorkExperience}); dates are months. */
@Getter
@Setter
@Entity
@Table(name = "work_experiences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkExperience extends UserAuditableEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "company", nullable = false, length = 80)
    private String company;

    @Column(name = "role", nullable = false, length = 80)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", length = 30)
    private Province province;

    @Convert(converter = YearMonthDateConverter.class)
    @Column(name = "start_date", nullable = false)
    private YearMonth startDate;

    /** {@code null} while current. */
    @Convert(converter = YearMonthDateConverter.class)
    @Column(name = "end_date")
    private YearMonth endDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "description", length = 500)
    private String description;

    WorkExperience(Candidate candidate) {
        this.candidate = candidate;
    }
}
