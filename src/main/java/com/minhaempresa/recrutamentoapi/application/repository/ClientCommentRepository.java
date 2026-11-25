package com.minhaempresa.recrutamentoapi.application.repository;

import com.minhaempresa.recrutamentoapi.application.entity.ClientComment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientCommentRepository extends JpaRepository<ClientComment, UUID> {

    /** Comments of the applications, the oldest first (conversation order). */
    List<ClientComment> findByApplicationIdInOrderByCreatedAtAscIdAsc(Collection<UUID> applicationIds);

    /** Erasure (D8-8): the text may hold personal data. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ClientComment c WHERE c.applicationId IN :applicationIds")
    int deleteByApplicationIds(@Param("applicationIds") Collection<UUID> applicationIds);
}
