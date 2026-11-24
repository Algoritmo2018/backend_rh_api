package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.job.repository.JobRepository;
import com.minhaempresa.recrutamentoapi.settings.service.TagUsageContributor;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Jobs per tag, for the tag usage shown before a tag is deleted. */
@Component
@RequiredArgsConstructor
class JobTagUsageContributor implements TagUsageContributor {

    private final JobRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countByTag() {
        return repository.countByTag().stream().collect(Collectors.toMap(JobRepository.TagCount::getTagId, JobRepository.TagCount::getTotal));
    }
}
