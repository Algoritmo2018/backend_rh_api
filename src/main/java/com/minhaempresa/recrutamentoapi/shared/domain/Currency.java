package com.minhaempresa.recrutamentoapi.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Currencies used for salaries, offers and fees. Amounts in different currencies are never summed. */
public enum Currency implements WireEnum {
    /** Angolan kwanza. */
    AOA("AOA"),
    /** US dollar (expatriate positions). */
    USD("USD");

    private final String value;

    Currency(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Currency fromValue(String value) {
        return WireEnums.fromValue(Currency.class, value);
    }
}
