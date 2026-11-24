package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateDocument;
import com.minhaempresa.recrutamentoapi.candidate.entity.Consent;
import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.ConsentRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.DocumentRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.ErasureLogRepository;
import com.minhaempresa.recrutamentoapi.candidate.repository.ReferenceRepository;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageCleanup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure of a candidate (Lei n.º 22/11): the extension point used by account deletion (stage 12) and
 * by the retention job (stage 15). Never exposed by a controller.
 *
 * <p>In one transaction: the profile is anonymized ("Candidato removido", kept so applications and
 * reports keep their numbers), documents and references are deleted, active consents are revoked
 * (the history of what was accepted stays, without personal data), and every
 * {@link CandidateErasureContributor} erases its own data. Files leave the bucket after the commit.
 * An {@link ErasureLog} row records when, why and who — without any personal data.
 */
@Service
@RequiredArgsConstructor
public class CandidateErasureService {

    private final CandidateRepository candidateRepository;
    private final DocumentRepository documentRepository;
    private final ReferenceRepository referenceRepository;
    private final ConsentRepository consentRepository;
    private final ErasureLogRepository erasureLogRepository;
    private final ObjectProvider<CandidateErasureContributor> contributors;
    private final StorageCleanup storageCleanup;
    private final Messages messages;
    private final Clock clock;

    /**
     * @param executedBy user who asked for it (the holder or an administrator); {@code null} for the
     *     scheduled retention job
     */
    @Transactional
    public void anonymize(UUID candidateId, ErasureLog.Reason reason, UUID executedBy) {
        Candidate candidate = candidateRepository.findById(candidateId).orElseThrow(() -> new NotFoundException("candidate.not-found"));
        Instant now = clock.instant();

        contributors.orderedStream().forEach(contributor -> contributor.eraseCandidate(candidateId));

        List<CandidateDocument> documents = documentRepository.findByCandidateIdOrderByUploadedAtDescIdDesc(candidateId);
        candidate.anonymize(messages.get("candidate.removed-name"), "removido-" + UUID.randomUUID() + "@anonimizado.invalid");
        // The profile no longer points to the documents before they are deleted
        candidateRepository.flush();
        documentRepository.deleteAll(documents);
        referenceRepository.deleteAll(referenceRepository.findByCandidateIdOrderByCreatedAtDescIdDesc(candidateId));
        consentRepository.findByCandidateIdOrderByGrantedAtDescIdDesc(candidateId).stream()
                .filter(Consent::isActive)
                .forEach(c -> c.revoke(now));

        storageCleanup.deleteAfterCommit(documents.stream().map(CandidateDocument::getStorageKey).toList());
        erasureLogRepository.save(new ErasureLog(now, reason, executedBy, documents.size()));
        candidateRepository.flush();
    }
}
