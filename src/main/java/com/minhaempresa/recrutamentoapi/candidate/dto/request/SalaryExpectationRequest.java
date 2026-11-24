package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.shared.domain.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Salary expectation as the skills form sends it ({@code {amount, currency}}).
 *
 * @param amount {@code null} = no expectation (removes the current one)
 */
public record SalaryExpectationRequest(
        @DecimalMin(value = "0.01", message = "{validation.money.invalid}") @Digits(integer = 17, fraction = 2, message = "{validation.money.invalid}") BigDecimal amount,
        @NotNull(message = "{validation.required}") Currency currency) {}
