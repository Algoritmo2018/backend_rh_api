package com.minhaempresa.recrutamentoapi.interview.service;

import com.minhaempresa.recrutamentoapi.application.service.InterviewerApplicationAccess;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateAccessContributor;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * An interviewer of an interview sees that candidate (restricted view, D6-1) and reads that application, also when not
 * assigned to the job (stage 9). Cancelled interviews do not count.
 */
@Component
@RequiredArgsConstructor
class InterviewAccessContributor implements CandidateAccessContributor, InterviewerApplicationAccess {

    private final InterviewService interviewService;

    @Override
    public Set<UUID> candidatesVisibleTo(UUID interviewerId) {
        return interviewService.candidateIdsOfInterviewer(interviewerId);
    }

    @Override
    public Set<UUID> applicationIdsOf(UUID interviewerId) {
        return interviewService.applicationIdsOfInterviewer(interviewerId);
    }
}
