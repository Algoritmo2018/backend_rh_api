package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.minhaempresa.recrutamentoapi.job.entity.SalaryPeriod;
import com.minhaempresa.recrutamentoapi.shared.domain.Currency;
import java.math.BigDecimal;

/** Frontend {@code SalaryRange}. */
public record SalaryRangeResponse(BigDecimal min, BigDecimal max, Currency currency, SalaryPeriod period, boolean visible) {}
