package com.minhaempresa.recrutamentoapi.shared.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Angolan mobile numbers, same rules as the frontend ({@code lib/format.ts}, {@code lib/validation.ts}):
 * 9 digits starting with 9, optionally prefixed by 244 / +244, stored in E.164 ({@code +244923456789}).
 */
public final class PhoneNumbers {

    private static final Pattern LOCAL_MOBILE = Pattern.compile("^9\\d{8}$");
    private static final String COUNTRY_CODE = "244";

    private PhoneNumbers() {}

    /** E.164 form of a valid Angolan mobile number, or empty if the value is not one. */
    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith(COUNTRY_CODE) && digits.length() == 12) {
            digits = digits.substring(COUNTRY_CODE.length());
        }
        return LOCAL_MOBILE.matcher(digits).matches() ? Optional.of("+" + COUNTRY_CODE + digits) : Optional.empty();
    }

    public static boolean isValid(String value) {
        return normalize(value).isPresent();
    }

    /** E.164 form; only call after validation. */
    public static String normalizeOrThrow(String value) {
        return normalize(value).orElseThrow(() -> new IllegalArgumentException("Invalid Angolan mobile number"));
    }
}
