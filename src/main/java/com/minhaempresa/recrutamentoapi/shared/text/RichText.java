package com.minhaempresa.recrutamentoapi.shared.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Limited rich text written in the frontend editor (job descriptions): only {@code p, strong, em, ul,
 * li, br}, without any attribute. Everything else (scripts, images, links, styles, event handlers) is
 * removed; text is kept. Sanitize <em>before</em> storing, so the database never holds other HTML.
 */
public final class RichText {

    private static final Safelist ALLOWED = Safelist.none().addTags("p", "strong", "em", "ul", "li", "br");
    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    private RichText() {}

    /** HTML reduced to the allowed tags (no attributes). */
    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, "", ALLOWED, OUTPUT).trim();
    }

    /** Visible text of the HTML (whitespace collapsed), e.g. for search and minimum-length checks. */
    public static String toText(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }
}
