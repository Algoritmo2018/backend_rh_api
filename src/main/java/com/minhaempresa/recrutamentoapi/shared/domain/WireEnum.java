package com.minhaempresa.recrutamentoapi.shared.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum whose value "on the wire" (JSON, query parameters) differs from the Java constant name.
 *
 * <p>Convention: Java constant in UPPER_SNAKE_CASE ({@code FIXED_TERM}), wire value identical to the
 * frontend ({@code fixed_term}), database column storing the constant name
 * ({@code @Enumerated(EnumType.STRING)}, never {@code ORDINAL}).
 *
 * <p>Each implementing enum must also declare a {@code @JsonCreator} factory delegating to
 * {@link WireEnums#fromValue(Class, String)}.
 */
public interface WireEnum {

    /** Value used in JSON and query parameters, identical to the frontend. */
    @JsonValue
    String getValue();
}
