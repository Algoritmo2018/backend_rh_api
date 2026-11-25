package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.repository.ApplicationRepository;
import com.minhaempresa.recrutamentoapi.settings.service.TagUsageContributor;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applications per tag, for the tag usage shown before a tag is deleted. */
@Component
@RequiredArgsConstructor
class ApplicationTagUsageContributor implements TagUsageContributor {

    private final ApplicationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countByTag() {
        return repository.countByTag().stream().collect(Collectors.toMap(ApplicationRepository.TagCount::getTagId, ApplicationRepository.TagCount::getTotal));
    }
}
