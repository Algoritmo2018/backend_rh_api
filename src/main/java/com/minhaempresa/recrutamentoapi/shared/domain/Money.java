package com.minhaempresa.recrutamentoapi.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

/**
 * Amount of money in a given currency. Matches the frontend type {@code { amount, currency }}.
 *
 * <p>Stored as {@code NUMERIC(19,2)} plus a 3-letter currency code. Every entity that embeds it must
 * rename the columns with {@code @AttributeOverride} (e.g. {@code salary_amount},
 * {@code salary_currency}).
 *
 * @param amount the amount, never negative
 * @param currency the currency
 */
@Embeddable
public record Money(
        @Column(precision = 19, scale = 2) BigDecimal amount,
        @Enumerated(EnumType.STRING) @Column(length = 3) Currency currency) {}
