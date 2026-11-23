package com.minhaempresa.recrutamentoapi.settings.repository;

import com.minhaempresa.recrutamentoapi.settings.entity.RejectionReason;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RejectionReasonRepository extends JpaRepository<RejectionReason, UUID> {

    List<RejectionReason> findAllByOrderByPositionAsc();

    List<RejectionReason> findByActiveTrueOrderByPositionAsc();

    boolean existsByCode(String code);

    @Query("select coalesce(max(r.position), 0) from RejectionReason r")
    int maxPosition();
}
