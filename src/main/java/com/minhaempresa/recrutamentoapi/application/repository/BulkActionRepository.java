package com.minhaempresa.recrutamentoapi.application.repository;

import com.minhaempresa.recrutamentoapi.application.entity.BulkAction;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BulkActionRepository extends JpaRepository<BulkAction, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);

    /** The oldest action still in the window: the limit frees up when it leaves the window. */
    Optional<BulkAction> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID userId, Instant after);

    @Modifying
    @Query("DELETE FROM BulkAction b WHERE b.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
