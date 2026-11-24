package com.minhaempresa.recrutamentoapi.client.repository;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequest;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestStatus;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters of the contact-request list; a {@code null} value means "no filter". */
public final class ContactRequestSpecifications {

    private ContactRequestSpecifications() {}

    public static Specification<ContactRequest> kind(ContactRequestKind kind) {
        return (root, query, cb) -> kind == null ? null : cb.equal(root.get("kind"), kind);
    }

    public static Specification<ContactRequest> status(ContactRequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
