package com.minhaempresa.recrutamentoapi.candidate.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Talent search in SQL (full-text {@code pt_unaccent}, relevance per word). */
public interface CandidateRepositoryCustom {

    /**
     * One page of the talent pool (only candidates with {@code in_talent_pool}), most relevant first,
     * then most experienced.
     */
    TalentSearchPage searchTalent(TalentCriteria criteria, long offset, int limit);

    /**
     * Words of the search that keep a lexeme after the stop words of {@code pt_unaccent} are removed
     * ({@code "sem"} is dropped): only those count for the relevance.
     */
    List<String> withLexemes(List<String> words);

    /**
     * Filters, already normalized by the service; {@code null} or empty = no filter.
     *
     * @param words search words (letters and digits only, lower case, no accents)
     * @param licenseWords words meaning "has a driver license" (matched by the license list)
     * @param skills each one must be part of a skill (lower case, no accents)
     * @param provinces constant names
     * @param educationLevels constant names of the levels that satisfy the minimum
     * @param languages each: language key plus the constant names of the levels that satisfy it
     * @param availability constant names
     */
    record TalentCriteria(
            List<String> words,
            List<String> licenseWords,
            List<String> skills,
            List<String> provinces,
            Integer minYears,
            Integer maxYears,
            List<String> educationLevels,
            List<LanguageCriterion> languages,
            List<String> availability,
            Set<UUID> tagIds) {}

    record LanguageCriterion(String languageKey, List<String> levels) {}

    /** @param matchedTerms per hit, the search words found */
    record TalentHit(UUID id, int relevance, List<String> matchedTerms) {}

    record TalentSearchPage(List<TalentHit> hits, long total) {}
}
