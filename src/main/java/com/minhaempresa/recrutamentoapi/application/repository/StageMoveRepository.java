package com.minhaempresa.recrutamentoapi.application.repository;

import com.minhaempresa.recrutamentoapi.application.entity.StageMove;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StageMoveRepository extends JpaRepository<StageMove, UUID> {

    /** History of an application, the newest first (UUID v7 ids break ties of the same instant). */
    List<StageMove> findByApplicationIdOrderByCreatedAtDescIdDesc(UUID applicationId);

    /** Erasure: notes and reasons may hold personal data. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE StageMove m SET m.reason = NULL WHERE m.applicationId IN :applicationIds")
    int clearReasons(@Param("applicationIds") Collection<UUID> applicationIds);
}
