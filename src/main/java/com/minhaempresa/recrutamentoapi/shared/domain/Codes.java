package com.minhaempresa.recrutamentoapi.shared.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Stable {@code code} values for catalog rows created by users. */
public final class Codes {

    private static final int MAX_LENGTH = 60;

    private Codes() {}

    /** {@code "Não cumpre os requisitos"} → {@code "nao-cumpre-os-requisitos"}. */
    public static String slugify(String value) {
        String slug = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "item" : slug;
    }

    /** Slug of {@code value}, with {@code -2}, {@code -3}… appended while {@code taken} says it exists. */
    public static String unique(String value, Predicate<String> taken) {
        String base = slugify(value);
        String code = base;
        for (int n = 2; taken.test(code); n++) {
            code = base + "-" + n;
        }
        return code;
    }
}
