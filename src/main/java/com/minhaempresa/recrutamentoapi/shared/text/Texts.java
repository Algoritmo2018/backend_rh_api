package com.minhaempresa.recrutamentoapi.shared.text;

import java.text.Normalizer;
import java.util.Locale;

/** Text comparisons like the frontend {@code normalizeText}: lower case, no accents, trimmed. */
public final class Texts {

    private Texts() {}

    /** {@code " Inglês "} → {@code "ingles"} ({@code null} → {@code ""}). */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** Escapes {@code \}, {@code %} and {@code _} for a {@code LIKE … ESCAPE '\'} pattern. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code null} for {@code null} or blank, the trimmed value otherwise. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
