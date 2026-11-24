package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.client.service.ClientStatsContributor;
import com.minhaempresa.recrutamentoapi.job.repository.JobRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code openJobs} and {@code totalJobs} of the client list. */
@Component
@RequiredArgsConstructor
class JobClientStatsContributor implements ClientStatsContributor {

    private final JobRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ClientCounts> countsByClient(Collection<UUID> clientIds) {
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        return repository.countByClient(clientIds).stream()
                .collect(Collectors.toMap(JobRepository.ClientJobCount::getClientId, c -> new ClientCounts(c.getOpen(), c.getTotal(), 0)));
    }
}
