package com.minhaempresa.recrutamentoapi.job.repository;

import com.minhaempresa.recrutamentoapi.job.entity.Job;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Public search in SQL: the generated {@code search_vector} column (configuration {@code pt_unaccent},
 * GIN index) is not mapped in the entity. Only ids are returned; the service loads the jobs.
 */
class JobRepositoryCustomImpl implements JobRepositoryCustom {

    private final NamedParameterJdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    JobRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PublicSearchPage searchPublic(PublicSearch search, long offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("today", search.today());
        StringBuilder where = new StringBuilder(" FROM jobs j WHERE j.status = 'OPEN' AND (j.deadline IS NULL OR j.deadline >= :today)");
        if (search.tsQuery() != null && hasSearchTerms(search.tsQuery())) {
            where.append(" AND j.search_vector @@ to_tsquery('pt_unaccent', :tsQuery)");
            params.addValue("tsQuery", search.tsQuery());
        }
        filter(where, params, "province", search.province() == null ? null : search.province().name());
        filter(where, params, "area", search.area() == null ? null : search.area().name());
        filter(where, params, "contract_type", search.contractType() == null ? null : search.contractType().name());
        filter(where, params, "work_mode", search.workMode() == null ? null : search.workMode().name());
        filter(where, params, "experience_level", search.experienceLevel() == null ? null : search.experienceLevel().name());
        if (search.publishedSince() != null) {
            where.append(" AND j.published_at >= :publishedSince");
            params.addValue("publishedSince", Timestamp.from(search.publishedSince()));
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, params, Long.class);
        if (total == null || total == 0 || offset >= total) {
            return new PublicSearchPage(List.of(), total == null ? 0 : total);
        }
        String order = switch (search.sort()) {
            case DEADLINE -> " ORDER BY j.deadline ASC NULLS LAST, j.published_at DESC, j.id";
            case TITLE -> " ORDER BY unaccent(lower(j.title)), j.id";
            case RECENT -> " ORDER BY j.published_at DESC, j.id DESC";
        };
        params.addValue("limit", limit).addValue("offset", offset);
        List<UUID> ids = jdbc.queryForList("SELECT j.id" + where + order + " LIMIT :limit OFFSET :offset", params, UUID.class);
        return new PublicSearchPage(ids, total);
    }

    @Override
    public void forceVersionIncrement(Job job) {
        entityManager.lock(job, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    /**
     * Whether the query keeps any term after the stop words are removed ({@code "de:*"} → empty). An
     * empty query would match nothing, so the text filter is then ignored.
     */
    private boolean hasSearchTerms(String tsQuery) {
        Integer nodes = jdbc.queryForObject("SELECT numnode(to_tsquery('pt_unaccent', :tsQuery))",
                new MapSqlParameterSource("tsQuery", tsQuery), Integer.class);
        return nodes != null && nodes > 0;
    }

    /** {@code column} is a constant of this class, never user input. */
    private static void filter(StringBuilder where, MapSqlParameterSource params, String column, String value) {
        if (value != null) {
            where.append(" AND j.").append(column).append(" = :").append(column);
            params.addValue(column, value);
        }
    }
}
