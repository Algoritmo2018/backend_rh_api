package com.minhaempresa.recrutamentoapi.settings.service;

import com.minhaempresa.recrutamentoapi.settings.dto.request.TagRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.response.TagResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.Tag;
import com.minhaempresa.recrutamentoapi.settings.mapper.CatalogMapper;
import com.minhaempresa.recrutamentoapi.settings.repository.TagRepository;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tags. Names are unique (case-insensitive). Deleting really deletes: the link tables of jobs,
 * candidates and applications reference tags with {@code ON DELETE CASCADE}.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository repository;
    private final CatalogMapper mapper;
    private final List<TagUsageContributor> usageContributors;

    @Cacheable(CacheNames.TAGS)
    @Transactional(readOnly = true)
    public List<TagResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    /** Records using each tag, summed over every module (0 for unused tags). */
    @Transactional(readOnly = true)
    public Map<UUID, Long> usage() {
        Map<UUID, Long> usage = new LinkedHashMap<>();
        repository.findAllByOrderByNameAsc().forEach(tag -> usage.put(tag.getId(), 0L));
        for (TagUsageContributor contributor : usageContributors) {
            contributor.countByTag().forEach((tagId, count) -> usage.computeIfPresent(tagId, (id, total) -> total + count));
        }
        return usage;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.TAGS, allEntries = true)
    public TagResponse create(TagRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw nameTaken();
        }
        return mapper.toResponse(repository.save(new Tag(name, request.color())));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.TAGS, allEntries = true)
    public TagResponse update(UUID id, TagRequest request) {
        Tag tag = find(id);
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw nameTaken();
        }
        tag.setName(name);
        tag.setColor(request.color());
        return mapper.toResponse(tag);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.TAGS, allEntries = true)
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    private Tag find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("settings.tag.not-found"));
    }

    private static ConflictException nameTaken() {
        return new ConflictException("settings.tag.name-taken", List.of(new FieldError("name", "settings.tag.name-taken")));
    }
}
