package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words of the talent search, as the frontend ({@code candidatesService.relevanceFor}): lower case, no
 * accents, at least 2 characters, without the Portuguese stop words; "carta"/"conducao" mean "has a
 * driver license" (synonyms of the talent search).
 */
final class TalentSearchTerms {

    /** Stop words of the frontend search. */
    static final Set<String> STOP_WORDS = Set.of("de", "da", "do", "das", "dos", "e", "em", "com", "a", "o", "as", "os", "para", "no", "na", "um", "uma", "que");

    /** Words that mean "has a driver license" (matched by the license list, not by the text). */
    static final Set<String> LICENSE_WORDS = Set.of("carta", "conducao", "cartadeconducao");

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int MAX_WORDS = 8;

    private TalentSearchTerms() {}

    /** Search words in order, without repetitions. */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        Matcher matcher = WORD.matcher(Texts.fold(text));
        while (matcher.find() && words.size() < MAX_WORDS) {
            String word = matcher.group();
            if (word.length() > 1 && !STOP_WORDS.contains(word) && !words.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }
}
