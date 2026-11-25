package com.minhaempresa.recrutamentoapi.messaging.repository;

import com.minhaempresa.recrutamentoapi.messaging.entity.Message;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageDirection;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the message list (inbox). */
public final class MessageSpecifications {

    private MessageSpecifications() {}

    /**
     * @param search text in the subject or body, or a candidate among {@code candidatesNamed} (already
     *     resolved by name); {@code null} = no text filter
     */
    public static Specification<Message> matching(UUID candidateId, UUID applicationId, MessageChannel channel,
            MessageDirection direction, String search, Collection<UUID> candidatesNamed) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (candidateId != null) {
                predicates.add(cb.equal(root.get("candidateId"), candidateId));
            }
            if (applicationId != null) {
                predicates.add(cb.equal(root.get("applicationId"), applicationId));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>();
                any.add(cb.like(cb.lower(root.get("subject")), pattern, '\\'));
                any.add(cb.like(cb.lower(root.get("body")), pattern, '\\'));
                if (!candidatesNamed.isEmpty()) {
                    any.add(root.get("candidateId").in(candidatesNamed));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
