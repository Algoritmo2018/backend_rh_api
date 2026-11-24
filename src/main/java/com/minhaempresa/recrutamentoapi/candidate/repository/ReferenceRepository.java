package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateReference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceRepository extends JpaRepository<CandidateReference, UUID> {

    /** References of a candidate, newest first. */
    List<CandidateReference> findByCandidateIdOrderByCreatedAtDescIdDesc(UUID candidateId);
}
