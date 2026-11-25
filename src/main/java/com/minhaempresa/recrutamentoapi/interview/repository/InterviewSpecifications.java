package com.minhaempresa.recrutamentoapi.interview.repository;

import com.minhaempresa.recrutamentoapi.interview.entity.Interview;
import com.minhaempresa.recrutamentoapi.interview.entity.InterviewStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the agenda ({@code GET /interviews}). */
public final class InterviewSpecifications {

    private InterviewSpecifications() {}

    /**
     * @param jobIds jobs of the {@code clientId} filter ({@code null} = no client filter)
     */
    public static Specification<Interview> matching(Instant from, Instant to, UUID interviewerId, UUID candidateId, UUID applicationId, UUID jobId,
            Collection<UUID> jobIds, Collection<InterviewStatus> statuses) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startsAt"), to));
            }
            if (interviewerId != null) {
                Join<Interview, UUID> interviewers = root.join("interviewerIds");
                predicates.add(cb.equal(interviewers, interviewerId));
                query.distinct(true);
            }
            if (candidateId != null) {
                predicates.add(cb.equal(root.get("candidateId"), candidateId));
            }
            if (applicationId != null) {
                predicates.add(cb.equal(root.get("applicationId"), applicationId));
            }
            if (jobId != null) {
                predicates.add(cb.equal(root.get("jobId"), jobId));
            }
            if (jobIds != null) {
                predicates.add(jobIds.isEmpty() ? cb.disjunction() : root.get("jobId").in(jobIds));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
