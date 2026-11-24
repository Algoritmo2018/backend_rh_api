package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    /** Accesses to the documents of a candidate, newest first. */
    List<DocumentAccess> findByCandidateIdOrderByAccessedAtDesc(UUID candidateId);
}
