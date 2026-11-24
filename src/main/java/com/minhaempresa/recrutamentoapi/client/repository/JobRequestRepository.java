package com.minhaempresa.recrutamentoapi.client.repository;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRequestRepository extends JpaRepository<JobRequest, UUID>, JpaSpecificationExecutor<JobRequest> {

    /** Requests of one client (client portal: the id always comes from the access token). */
    List<JobRequest> findByClientId(UUID clientId, Sort sort);

    /** A request only if it belongs to the client: another client's request is "not found". */
    Optional<JobRequest> findByIdAndClientId(UUID id, UUID clientId);

    /**
     * The request with a row lock ({@code SELECT … FOR UPDATE}) until the transaction ends: two jobs
     * created from the same request at the same time are serialized, and the second sees it converted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM JobRequest r WHERE r.id = :id")
    Optional<JobRequest> findByIdForUpdate(@Param("id") UUID id);
}
