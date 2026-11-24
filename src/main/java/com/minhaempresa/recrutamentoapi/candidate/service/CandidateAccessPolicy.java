package com.minhaempresa.recrutamentoapi.candidate.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Candidates visible to an interviewer: the union of what the contributing modules allow. */
@Component
@RequiredArgsConstructor
public class CandidateAccessPolicy {

    private final ObjectProvider<CandidateAccessContributor> contributors;

    public Set<UUID> visibleToInterviewer(UUID interviewerId) {
        Set<UUID> visible = new HashSet<>();
        contributors.orderedStream().forEach(c -> visible.addAll(c.candidatesVisibleTo(interviewerId)));
        return visible;
    }

    public boolean interviewerMaySee(UUID interviewerId, UUID candidateId) {
        return visibleToInterviewer(interviewerId).contains(candidateId);
    }
}
