package com.minhaempresa.recrutamentoapi.user.repository;

import com.minhaempresa.recrutamentoapi.user.entity.User;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the user list. */
public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> roleIn(Collection<UserRole> roles) {
        return (root, query, cb) -> root.get("role").in(roles);
    }

    public static Specification<User> clientId(UUID clientId) {
        return (root, query, cb) -> clientId == null ? cb.conjunction() : cb.equal(root.get("clientId"), clientId);
    }
}
