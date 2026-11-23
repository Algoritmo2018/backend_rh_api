package com.minhaempresa.recrutamentoapi.shared.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lookup of {@link WireEnum} constants by their wire value. */
public final class WireEnums {

    private static final ClassValue<Map<String, Enum<?>>> BY_VALUE = new ClassValue<>() {
        @Override
        protected Map<String, Enum<?>> computeValue(Class<?> type) {
            return Arrays.stream(type.getEnumConstants())
                    .collect(Collectors.toUnmodifiableMap(c -> ((WireEnum) c).getValue(), c -> (Enum<?>) c));
        }
    };

    private WireEnums() {}

    /**
     * Returns the constant whose wire value is {@code value}.
     *
     * @throws IllegalArgumentException if no constant matches (Jackson turns it into a 400 response)
     */
    public static <E extends Enum<E> & WireEnum> E fromValue(Class<E> type, String value) {
        return type.cast(lookup(type, value));
    }

    /** Untyped variant used by the MVC converter. */
    public static Object fromValueOfType(Class<?> type, String value) {
        if (!type.isEnum() || !WireEnum.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(type.getName() + " is not a WireEnum");
        }
        return lookup(type, value);
    }

    private static Enum<?> lookup(Class<?> type, String value) {
        Enum<?> constant = value == null ? null : BY_VALUE.get(type).get(value);
        if (constant == null) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);
        }
        return constant;
    }

    /** All wire values of an enum, in declaration order (useful for documentation and tests). */
    public static <E extends Enum<E> & WireEnum> List<String> values(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(WireEnum::getValue).toList();
    }
}
