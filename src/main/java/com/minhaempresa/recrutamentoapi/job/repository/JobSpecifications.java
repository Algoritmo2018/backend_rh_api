package com.minhaempresa.recrutamentoapi.job.repository;

import com.minhaempresa.recrutamentoapi.job.entity.Job;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the internal job list. A {@code null} value means "no filter". */
public final class JobSpecifications {

    private JobSpecifications() {}

    public static Specification<Job> status(JobStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Job> clientId(UUID clientId) {
        return (root, query, cb) -> clientId == null ? null : cb.equal(root.get("clientId"), clientId);
    }

    public static Specification<Job> recruiterId(UUID recruiterId) {
        return (root, query, cb) -> recruiterId == null ? null : cb.equal(root.get("recruiterId"), recruiterId);
    }

    /**
     * Title contains the term (case and accents ignored) or the job belongs to one of the given clients
     * (whose names matched the term).
     */
    public static Specification<Job> titleOrClient(String term, Collection<UUID> matchingClientIds) {
        return text(term, matchingClientIds, false);
    }

    /** Same as {@link #titleOrClient} plus the city (global search of the top bar). */
    public static Specification<Job> titleCityOrClient(String term, Collection<UUID> matchingClientIds) {
        return text(term, matchingClientIds, true);
    }

    private static Specification<Job> text(String term, Collection<UUID> matchingClientIds, boolean withCity) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return null;
            }
            var pattern = cb.function("unaccent", String.class, cb.literal(
                    "%" + term.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"));
            var match = cb.like(cb.function("unaccent", String.class, cb.lower(root.get("title"))), pattern, '\\');
            if (withCity) {
                match = cb.or(match, cb.like(cb.function("unaccent", String.class, cb.lower(root.get("city"))), pattern, '\\'));
            }
            return matchingClientIds.isEmpty() ? match : cb.or(match, root.get("clientId").in(matchingClientIds));
        };
    }
}
