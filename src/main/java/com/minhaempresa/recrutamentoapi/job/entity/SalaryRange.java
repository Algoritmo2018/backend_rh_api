package com.minhaempresa.recrutamentoapi.job.entity;

import com.minhaempresa.recrutamentoapi.shared.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

/**
 * Salary range of a job (frontend {@code SalaryRange}). Not a {@code Money}: it has two amounts, a
 * period and the public visibility flag.
 *
 * @param visible shown on the public job page
 */
@Embeddable
public record SalaryRange(
        @Column(name = "salary_min", precision = 19, scale = 2) BigDecimal min,
        @Column(name = "salary_max", precision = 19, scale = 2) BigDecimal max,
        @Enumerated(EnumType.STRING) @Column(name = "salary_currency", length = 3) Currency currency,
        @Enumerated(EnumType.STRING) @Column(name = "salary_period", length = 5) SalaryPeriod period,
        @Column(name = "salary_visible") Boolean visible) {}
