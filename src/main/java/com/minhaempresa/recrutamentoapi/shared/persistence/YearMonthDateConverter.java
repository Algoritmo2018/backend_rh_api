package com.minhaempresa.recrutamentoapi.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.YearMonth;

/** A month ({@code "YYYY-MM"} in JSON) stored in a {@code DATE} column as its first day. */
@Converter
public class YearMonthDateConverter implements AttributeConverter<YearMonth, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth month) {
        return month == null ? null : month.atDay(1);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate date) {
        return date == null ? null : YearMonth.from(date);
    }
}
