package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateAccessContributor;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** An interviewer sees the candidates who applied to the jobs where they are an interviewer (D6-1). */
@Component
@RequiredArgsConstructor
class ApplicationAccessContributor implements CandidateAccessContributor {

    private final ApplicationRepository repository;
    private final JobService jobService;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> candidatesVisibleTo(UUID interviewerId) {
        List<UUID> jobIds = jobService.jobIdsWithInterviewer(interviewerId);
        return jobIds.isEmpty() ? Set.of() : Set.copyOf(repository.findCandidateIdsByJobIdIn(jobIds));
    }
}
