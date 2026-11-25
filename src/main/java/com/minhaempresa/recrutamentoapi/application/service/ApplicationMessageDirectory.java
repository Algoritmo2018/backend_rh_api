package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.entity.Application;
import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.job.service.JobService;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageApplicationDirectory;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messages about an application: its candidate, the job title and the company for {@code {{empresa}}} (the
 * neutral text for a confidential job, never the real client name).
 */
@Component
@RequiredArgsConstructor
class ApplicationMessageDirectory implements MessageApplicationDirectory {

    private final ApplicationRepository repository;
    private final JobService jobService;
    private final Messages messages;

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicationRef> find(UUID applicationId) {
        return repository.findById(applicationId).flatMap(application -> jobService.findApplicable(application.getJobId())
                .map(job -> new ApplicationRef(application.getId(), application.getCandidateId(), job.title(),
                        job.confidential() || job.clientName() == null ? messages.get("application.company.confidential") : job.clientName())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> jobTitlesOf(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        List<Application> applications = repository.findAllById(Set.copyOf(applicationIds));
        Map<UUID, String> titles = jobService.titlesOf(applications.stream().map(Application::getJobId).distinct().toList());
        Map<UUID, String> result = new HashMap<>();
        applications.forEach(a -> {
            String title = titles.get(a.getJobId());
            if (title != null) {
                result.put(a.getId(), title);
            }
        });
        return result;
    }
}
