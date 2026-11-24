package com.minhaempresa.recrutamentoapi.candidate.repository;

import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateDocument;
import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<CandidateDocument, UUID> {

    /** Documents of a candidate, newest first. */
    List<CandidateDocument> findByCandidateIdOrderByUploadedAtDescIdDesc(UUID candidateId);

    /** A document only if it belongs to the candidate: another candidate's document is "not found". */
    Optional<CandidateDocument> findByIdAndCandidateId(UUID id, UUID candidateId);

    /** The newest other document of a type (new current CV or photo after a deletion). */
    Optional<CandidateDocument> findFirstByCandidateIdAndTypeAndIdNotOrderByUploadedAtDescIdDesc(UUID candidateId, DocumentType type, UUID excludedId);
}
