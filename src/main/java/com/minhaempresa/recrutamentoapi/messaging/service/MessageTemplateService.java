package com.minhaempresa.recrutamentoapi.messaging.service;

import com.minhaempresa.recrutamentoapi.messaging.dto.request.MessageTemplateRequest;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageTemplateResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageTemplate;
import com.minhaempresa.recrutamentoapi.messaging.event.MessageTemplateDeactivatedEvent;
import com.minhaempresa.recrutamentoapi.messaging.mapper.MessageTemplateMapper;
import com.minhaempresa.recrutamentoapi.messaging.repository.MessageTemplateRepository;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateValidator;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.domain.Codes;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Message templates (page Mensagens → Modelos). Plain text with {@code {{variables}}}; "delete"
 * deactivates. The list of active templates is cached.
 */
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateRepository repository;
    private final MessageTemplateMapper mapper;
    private final ApplicationEventPublisher events;

    /** Active templates, optionally of one channel (cached per channel). */
    @Cacheable(cacheNames = CacheNames.MESSAGE_TEMPLATES, key = "#channel == null ? 'ALL' : #channel.name()")
    @Transactional(readOnly = true)
    public List<MessageTemplateResponse> listActive(MessageChannel channel) {
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .filter(t -> channel == null || t.getChannel() == channel)
                .map(mapper::toResponse)
                .toList();
    }

    /** Active template by id (used by the pipeline auto-actions). */
    @Transactional(readOnly = true)
    public Optional<MessageTemplateResponse> findActive(UUID id) {
        return repository.findById(id).filter(MessageTemplate::isActive).map(mapper::toResponse);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.MESSAGE_TEMPLATES, CacheNames.PIPELINE_STAGES}, allEntries = true)
    public MessageTemplateResponse create(MessageTemplateRequest request) {
        validate(request);
        String code = Codes.unique(request.name(), repository::existsByCode);
        MessageTemplate template = new MessageTemplate(code, request.name().trim(), request.channel(), request.category(), subjectFor(request), request.body());
        return mapper.toResponse(repository.save(template));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.MESSAGE_TEMPLATES, CacheNames.PIPELINE_STAGES}, allEntries = true)
    public MessageTemplateResponse update(UUID id, MessageTemplateRequest request) {
        MessageTemplate template = findActiveEntity(id);
        validate(request);
        template.setName(request.name().trim());
        template.setChannel(request.channel());
        template.setCategory(request.category());
        template.setSubject(subjectFor(request));
        template.setBody(request.body());
        return mapper.toResponse(template);
    }

    /** "Delete": deactivates and switches off the automatic actions that sent it. */
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.MESSAGE_TEMPLATES, CacheNames.PIPELINE_STAGES}, allEntries = true)
    public void deactivate(UUID id) {
        MessageTemplate template = findActiveEntity(id);
        if (template.isSystem()) {
            throw new BusinessException("template.system");
        }
        template.setActive(false);
        events.publishEvent(new MessageTemplateDeactivatedEvent(template.getId()));
    }

    private MessageTemplate findActiveEntity(UUID id) {
        return repository.findById(id).filter(MessageTemplate::isActive).orElseThrow(() -> new NotFoundException("template.not-found"));
    }

    private static void validate(MessageTemplateRequest request) {
        List<FieldError> errors = TemplateValidator.validateMessageTemplate(request.channel(), request.subject(), request.body());
        if (!errors.isEmpty()) {
            throw new BusinessException("error.validation", errors);
        }
    }

    /** Only emails have a subject. */
    private static String subjectFor(MessageTemplateRequest request) {
        return request.channel() == MessageChannel.EMAIL ? request.subject().trim() : null;
    }
}
