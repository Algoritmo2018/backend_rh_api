package com.minhaempresa.recrutamentoapi.offer.service;

import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateValidator;
import com.minhaempresa.recrutamentoapi.offer.dto.request.OfferTemplateRequest;
import com.minhaempresa.recrutamentoapi.offer.dto.response.OfferTemplateResponse;
import com.minhaempresa.recrutamentoapi.offer.entity.OfferTemplate;
import com.minhaempresa.recrutamentoapi.offer.mapper.OfferTemplateMapper;
import com.minhaempresa.recrutamentoapi.offer.repository.OfferTemplateRepository;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.domain.Codes;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Offer letter templates (no deletion, as in the frontend). Marking one as default unmarks the others. */
@Service
@RequiredArgsConstructor
public class OfferTemplateService {

    private final OfferTemplateRepository repository;
    private final OfferTemplateMapper mapper;

    @Cacheable(CacheNames.OFFER_TEMPLATES)
    @Transactional(readOnly = true)
    public List<OfferTemplateResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OFFER_TEMPLATES, allEntries = true)
    public OfferTemplateResponse create(OfferTemplateRequest request) {
        validate(request);
        if (request.isDefault()) {
            repository.clearDefault();
        }
        String code = Codes.unique(request.name(), repository::existsByCode);
        return mapper.toResponse(repository.save(new OfferTemplate(code, request.name().trim(), request.body(), request.isDefault())));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OFFER_TEMPLATES, allEntries = true)
    public OfferTemplateResponse update(UUID id, OfferTemplateRequest request) {
        OfferTemplate template = repository.findById(id).orElseThrow(() -> new NotFoundException("template.not-found"));
        validate(request);
        if (request.isDefault()) {
            repository.clearDefaultExcept(id);
        }
        template.setName(request.name().trim());
        template.setBody(request.body());
        template.setDefault(request.isDefault());
        return mapper.toResponse(template);
    }

    private static void validate(OfferTemplateRequest request) {
        List<FieldError> errors = TemplateValidator.validateOfferTemplate(request.body());
        if (!errors.isEmpty()) {
            throw new BusinessException("error.validation", errors);
        }
    }
}
