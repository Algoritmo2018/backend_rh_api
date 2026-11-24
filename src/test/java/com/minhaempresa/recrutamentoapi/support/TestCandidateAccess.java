package com.minhaempresa.recrutamentoapi.support;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateAccessContributor;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureContributor;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the applications/interviews modules (stages 8 and 9) in tests: assigns candidates to
 * interviewers on demand (empty by default, so other tests are not affected) and records erasures.
 */
public class TestCandidateAccess implements CandidateAccessContributor, CandidateErasureContributor {

    private final Map<UUID, Set<UUID>> assignments = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<UUID> erased = new CopyOnWriteArrayList<>();

    public void assign(UUID interviewerId, UUID candidateId) {
        assignments.computeIfAbsent(interviewerId, id -> ConcurrentHashMap.newKeySet()).add(candidateId);
    }

    @Override
    public Set<UUID> candidatesVisibleTo(UUID interviewerId) {
        return Set.copyOf(assignments.getOrDefault(interviewerId, Set.of()));
    }

    @Override
    public void eraseCandidate(UUID candidateId) {
        erased.add(candidateId);
    }

    public boolean wasErased(UUID candidateId) {
        return erased.contains(candidateId);
    }
}
