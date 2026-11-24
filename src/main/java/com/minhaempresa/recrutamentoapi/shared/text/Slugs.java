package com.minhaempresa.recrutamentoapi.shared.text;

import java.text.Normalizer;
import java.util.Locale;

/** URL slugs, like the frontend {@code slugify}: "Técnico de HSE – Soyo" → "tecnico-de-hse-soyo". */
public final class Slugs {

    private Slugs() {}

    /** Lower-case ASCII letters and digits separated by single hyphens, at most {@code maxLength} characters. */
    public static String slugify(String value, int maxLength) {
        String slug = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        }
        return slug;
    }
}
