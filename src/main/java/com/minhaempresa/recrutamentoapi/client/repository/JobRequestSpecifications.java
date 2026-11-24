package com.minhaempresa.recrutamentoapi.client.repository;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequest;
import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters of the job-request list; a {@code null} value means "no filter". */
public final class JobRequestSpecifications {

    private JobRequestSpecifications() {}

    public static Specification<JobRequest> clientId(UUID clientId) {
        return (root, query, cb) -> clientId == null ? null : cb.equal(root.get("clientId"), clientId);
    }

    public static Specification<JobRequest> status(JobRequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
