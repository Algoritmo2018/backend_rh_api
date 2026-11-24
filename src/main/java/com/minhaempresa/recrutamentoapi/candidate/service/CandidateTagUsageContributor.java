package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.settings.service.TagUsageContributor;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Candidates per tag, for the tag usage shown before a tag is deleted. */
@Component
@RequiredArgsConstructor
class CandidateTagUsageContributor implements TagUsageContributor {

    private final CandidateRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countByTag() {
        return repository.countByTag().stream().collect(Collectors.toMap(CandidateRepository.TagCount::getTagId, CandidateRepository.TagCount::getTotal));
    }
}
