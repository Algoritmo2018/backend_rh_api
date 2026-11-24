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
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) {
                return null;
            }
            String pattern = "%" + term.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            var title = cb.like(
                    cb.function("unaccent", String.class, cb.lower(root.get("title"))),
                    cb.function("unaccent", String.class, cb.literal(pattern)),
                    '\\');
            return matchingClientIds.isEmpty() ? title : cb.or(title, root.get("clientId").in(matchingClientIds));
        };
    }
}
