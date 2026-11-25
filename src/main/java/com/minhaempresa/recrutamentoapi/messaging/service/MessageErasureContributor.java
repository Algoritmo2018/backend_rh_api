package com.minhaempresa.recrutamentoapi.messaging.service;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureContributor;
import com.minhaempresa.recrutamentoapi.messaging.repository.MessageRepository;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erasure of a candidate: every message to them is deleted, and so are their outbox emails (sent or still
 * pending, so nothing is delivered after the erasure).
 */
@Component
@RequiredArgsConstructor
public class MessageErasureContributor implements CandidateErasureContributor {

    private final MessageRepository repository;
    private final EmailOutbox outbox;

    @Override
    @Transactional
    public void eraseCandidate(UUID candidateId) {
        outbox.deleteByReferences(repository.findIdsByCandidateId(candidateId));
        repository.deleteByCandidateId(candidateId);
    }
}
