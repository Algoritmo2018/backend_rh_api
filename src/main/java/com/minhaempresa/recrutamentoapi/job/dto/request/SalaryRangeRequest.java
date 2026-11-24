package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.SalaryPeriod;
import com.minhaempresa.recrutamentoapi.job.validation.ValidSalaryRange;
import com.minhaempresa.recrutamentoapi.shared.domain.Currency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Salary of the job form (frontend {@code SalaryRange}); the whole object is {@code null} when the job
 * has no salary.
 *
 * @param visible shown on the public job page
 */
@ValidSalaryRange
public record SalaryRangeRequest(
        @Digits(integer = 17, fraction = 2, message = "{validation.money.invalid}") BigDecimal min,
        @Digits(integer = 17, fraction = 2, message = "{validation.money.invalid}") BigDecimal max,
        @NotNull(message = "{validation.required}") Currency currency,
        @NotNull(message = "{validation.required}") SalaryPeriod period,
        @NotNull(message = "{validation.required}") Boolean visible) {}
