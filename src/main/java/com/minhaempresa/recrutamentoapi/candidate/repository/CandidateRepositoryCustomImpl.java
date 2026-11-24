package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Talent search. Relevance as the frontend: share of the search words found in the profile
 * ({@code search_vector}, prefix match, accents ignored); words meaning "driver license" match when the
 * candidate has any license. Every filter is a bound parameter; only constant SQL is concatenated.
 */
class CandidateRepositoryCustomImpl implements CandidateRepositoryCustom {

    private final NamedParameterJdbcTemplate jdbc;

    CandidateRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TalentSearchPage searchTalent(TalentCriteria criteria, long offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>();
        List<String> matchedCases = new ArrayList<>();
        int index = 0;
        for (String word : criteria.words()) {
            String condition = "(c.search_vector @@ to_tsquery('pt_unaccent', :q" + index + "))";
            params.addValue("q" + index, word + ":*");
            params.addValue("w" + index, word);
            conditions.add(condition);
            matchedCases.add("CASE WHEN " + condition + " THEN CAST(:w" + index + " AS TEXT) END");
            index++;
        }
        for (String word : criteria.licenseWords()) {
            String condition = "(cardinality(c.driver_licenses) > 0)";
            params.addValue("w" + index, word);
            conditions.add(condition);
            matchedCases.add("CASE WHEN " + condition + " THEN CAST(:w" + index + " AS TEXT) END");
            index++;
        }
        String relevance = conditions.isEmpty()
                ? "100"
                : "CAST(round(CAST(" + String.join(" + ", conditions.stream().map(c -> "CAST(" + c + " AS INTEGER)").toList())
                        + " AS NUMERIC) * 100 / " + conditions.size() + ") AS INTEGER)";
        String matched = matchedCases.isEmpty()
                ? "CAST(ARRAY[] AS TEXT[])"
                : "array_remove(ARRAY[" + String.join(", ", matchedCases) + "], NULL)";

        StringBuilder where = new StringBuilder(" WHERE c.in_talent_pool");
        int skill = 0;
        for (String value : criteria.skills()) {
            where.append(" AND EXISTS (SELECT 1 FROM unnest(c.skills) AS sk WHERE unaccent(lower(sk)) LIKE :sk").append(skill).append(" ESCAPE '\\')");
            params.addValue("sk" + skill++, "%" + Texts.escapeLike(value) + "%");
        }
        if (!criteria.provinces().isEmpty()) {
            where.append(" AND c.province IN (:provinces)");
            params.addValue("provinces", criteria.provinces());
        }
        if (criteria.minYears() != null) {
            where.append(" AND c.total_experience_years >= :minYears");
            params.addValue("minYears", criteria.minYears());
        }
        if (criteria.maxYears() != null) {
            where.append(" AND c.total_experience_years <= :maxYears");
            params.addValue("maxYears", criteria.maxYears());
        }
        if (!criteria.educationLevels().isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM educations e WHERE e.candidate_id = c.id AND e.level IN (:educationLevels))");
            params.addValue("educationLevels", criteria.educationLevels());
        }
        int language = 0;
        for (LanguageCriterion criterion : criteria.languages()) {
            where.append(" AND EXISTS (SELECT 1 FROM candidate_languages l WHERE l.candidate_id = c.id AND l.language_key = :lk").append(language)
                    .append(" AND l.level IN (:ll").append(language).append("))");
            params.addValue("lk" + language, criterion.languageKey());
            params.addValue("ll" + language++, criterion.levels());
        }
        if (!criteria.availability().isEmpty()) {
            where.append(" AND c.availability IN (:availability)");
            params.addValue("availability", criteria.availability());
        }
        if (!criteria.tagIds().isEmpty()) {
            where.append(" AND (SELECT count(*) FROM candidate_tags t WHERE t.candidate_id = c.id AND t.tag_id IN (:tagIds)) = :tagCount");
            params.addValue("tagIds", criteria.tagIds());
            params.addValue("tagCount", criteria.tagIds().size());
        }

        String hits = "WITH hits AS (SELECT c.id, c.total_experience_years, " + relevance + " AS relevance, " + matched + " AS matched"
                + " FROM candidates c" + where + ") ";
        Long total = jdbc.queryForObject(hits + "SELECT count(*) FROM hits WHERE relevance >= 50", params, Long.class);
        if (total == null || total == 0 || offset >= total) {
            return new TalentSearchPage(List.of(), total == null ? 0 : total);
        }
        params.addValue("limit", limit).addValue("offset", offset);
        List<TalentHit> page = jdbc.query(
                hits + "SELECT id, relevance, matched FROM hits WHERE relevance >= 50"
                        + " ORDER BY relevance DESC, total_experience_years DESC, id LIMIT :limit OFFSET :offset",
                params,
                (rs, row) -> new TalentHit(rs.getObject("id", UUID.class), rs.getInt("relevance"), strings(rs.getArray("matched"))));
        return new TalentSearchPage(page, total);
    }

    @Override
    public List<String> withLexemes(List<String> words) {
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            Integer nodes = jdbc.queryForObject("SELECT numnode(to_tsquery('pt_unaccent', :q))", new MapSqlParameterSource("q", word + ":*"), Integer.class);
            if (nodes != null && nodes > 0) {
                kept.add(word);
            }
        }
        return kept;
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
