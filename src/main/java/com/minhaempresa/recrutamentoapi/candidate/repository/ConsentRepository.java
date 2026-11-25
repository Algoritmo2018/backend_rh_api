package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.candidate.entity.Consent;
import com.minhaempresa.recrutamentoapi.candidate.entity.ConsentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /** History of a candidate, most recent first. */
    List<Consent> findByCandidateIdOrderByGrantedAtDescIdDesc(UUID candidateId);

    /** The active general consent of a type (talent pool, marketing), if any. */
    Optional<Consent> findByCandidateIdAndTypeAndJobIdIsNullAndRevokedAtIsNull(UUID candidateId, ConsentType type);

    /** Active job-application consents of a candidate for a job, the newest first. */
    List<Consent> findByCandidateIdAndTypeAndJobIdAndRevokedAtIsNullOrderByGrantedAtDesc(UUID candidateId, ConsentType type, UUID jobId);
}
