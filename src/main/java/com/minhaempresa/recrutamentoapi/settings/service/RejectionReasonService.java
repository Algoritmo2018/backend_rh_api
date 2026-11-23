package com.minhaempresa.recrutamentoapi.settings.service;

import com.minhaempresa.recrutamentoapi.settings.dto.request.RejectionReasonRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.response.RejectionReasonResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.RejectionReason;
import com.minhaempresa.recrutamentoapi.settings.mapper.CatalogMapper;
import com.minhaempresa.recrutamentoapi.settings.repository.RejectionReasonRepository;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.domain.Codes;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rejection reasons: created and edited by the admin, deactivated instead of deleted. */
@Service
@RequiredArgsConstructor
public class RejectionReasonService {

    private final RejectionReasonRepository repository;
    private final CatalogMapper mapper;

    @Cacheable(cacheNames = CacheNames.REJECTION_REASONS, key = "#includeInactive")
    @Transactional(readOnly = true)
    public List<RejectionReasonResponse> list(boolean includeInactive) {
        List<RejectionReason> reasons = includeInactive ? repository.findAllByOrderByPositionAsc() : repository.findByActiveTrueOrderByPositionAsc();
        return reasons.stream().map(mapper::toResponse).toList();
    }

    /** The code is generated from the label once and never changes. */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.REJECTION_REASONS, allEntries = true)
    public RejectionReasonResponse create(RejectionReasonRequest request) {
        String label = request.label().trim();
        RejectionReason reason = new RejectionReason(Codes.unique(label, repository::existsByCode), label, request.category(), repository.maxPosition() + 1);
        reason.setActive(request.active() == null || request.active());
        return mapper.toResponse(repository.save(reason));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.REJECTION_REASONS, allEntries = true)
    public RejectionReasonResponse update(UUID id, RejectionReasonRequest request) {
        RejectionReason reason = repository.findById(id).orElseThrow(() -> new NotFoundException("settings.reason.not-found"));
        reason.setLabel(request.label().trim());
        reason.setCategory(request.category());
        if (request.active() != null) {
            reason.setActive(request.active());
        }
        return mapper.toResponse(reason);
    }
}
