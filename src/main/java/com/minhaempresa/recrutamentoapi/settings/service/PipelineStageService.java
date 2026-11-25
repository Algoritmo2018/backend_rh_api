package com.minhaempresa.recrutamentoapi.settings.service;

import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageTemplateResponse;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageTemplateService;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateValidator;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateVariable;
import com.minhaempresa.recrutamentoapi.settings.dto.request.PipelineStageUpdateRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.request.StageAutoActionRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.response.PipelineStageResponse;
import com.minhaempresa.recrutamentoapi.settings.dto.response.StageAutoActionResponse;
import com.minhaempresa.recrutamentoapi.settings.entity.PipelineStage;
import com.minhaempresa.recrutamentoapi.settings.entity.StageAutoActionType;
import com.minhaempresa.recrutamentoapi.settings.repository.PipelineStageRepository;
import com.minhaempresa.recrutamentoapi.shared.config.CacheNames;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pipeline stages (Configurações → Etapas do funil): rename, reorder and choose the automatic message.
 * The 7 keys are fixed, {@code new} stays first and {@code hired} last.
 */
@Service
@RequiredArgsConstructor
public class PipelineStageService {

    private static final int STAGE_COUNT = PipelineStageKey.values().length;

    private final PipelineStageRepository repository;
    private final MessageTemplateService messageTemplates;

    @Cacheable(CacheNames.PIPELINE_STAGES)
    @Transactional(readOnly = true)
    public List<PipelineStageResponse> list() {
        Map<UUID, MessageTemplateResponse> templates = activeTemplates();
        return repository.findAllByOrderByPositionAsc().stream().map(s -> toResponse(s, templates)).toList();
    }

    /** Saves the whole list at once (the frontend sends every stage). */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PIPELINE_STAGES, allEntries = true)
    public List<PipelineStageResponse> update(List<PipelineStageUpdateRequest> requests) {
        Map<UUID, PipelineStage> stages = repository.findAll().stream().collect(Collectors.toMap(PipelineStage::getId, Function.identity()));
        validateStructure(requests, stages);
        Map<UUID, MessageTemplateResponse> templates = activeTemplates();
        for (int i = 0; i < requests.size(); i++) {
            PipelineStageUpdateRequest request = requests.get(i);
            PipelineStage stage = stages.get(request.id());
            stage.setName(request.name().trim());
            stage.setPosition(request.order());
            stage.setAutoActionTemplateId(resolveTemplate(i, request.autoAction(), templates));
        }
        repository.flush();
        return repository.findAllByOrderByPositionAsc().stream().map(s -> toResponse(s, templates)).toList();
    }

    /** A template was deactivated: stages stop sending it (same transaction). */
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PIPELINE_STAGES, allEntries = true)
    public void clearAutoActionsUsing(UUID templateId) {
        repository.clearAutoActionTemplate(templateId);
    }

    private static void validateStructure(List<PipelineStageUpdateRequest> requests, Map<UUID, PipelineStage> stages) {
        Set<UUID> ids = new HashSet<>();
        requests.forEach(r -> ids.add(r.id()));
        if (requests.size() != STAGE_COUNT || !ids.equals(stages.keySet())) {
            throw new BusinessException("settings.stages.incomplete");
        }
        Set<Integer> orders = new HashSet<>();
        requests.forEach(r -> orders.add(r.order()));
        if (!orders.equals(IntStream.rangeClosed(1, STAGE_COUNT).boxed().collect(Collectors.toSet()))) {
            throw new BusinessException("settings.stages.order-invalid");
        }
        for (PipelineStageUpdateRequest request : requests) {
            PipelineStageKey key = stages.get(request.id()).getStageKey();
            if ((key == PipelineStageKey.NEW && request.order() != 1) || (key == PipelineStageKey.HIRED && request.order() != STAGE_COUNT)) {
                throw new BusinessException("settings.stages.fixed-position");
            }
        }
    }

    private static UUID resolveTemplate(int index, StageAutoActionRequest action, Map<UUID, MessageTemplateResponse> templates) {
        if (action.type() == StageAutoActionType.NONE) {
            return null;
        }
        String field = "[" + index + "].autoAction.templateId";
        MessageTemplateResponse template = action.templateId() == null ? null : templates.get(action.templateId());
        if (template == null) {
            throw new BusinessException("error.validation", List.of(new FieldError(field, "settings.stages.template-invalid")));
        }
        if (action.channel() != null && action.channel() != template.channel()) {
            throw new BusinessException("error.validation", List.of(new FieldError("[" + index + "].autoAction.channel", "settings.stages.channel-mismatch")));
        }
        if (usesUnavailableVariables(template)) {
            // D9-1: the interview (or offer) data is unknown when an application changes stage
            throw new BusinessException("error.validation", List.of(new FieldError(field, "settings.stages.template-needs-context")));
        }
        return template.id();
    }

    /** Variables a stage change cannot fill: interview (stage 9) and offer (stage 11) data. */
    private static final Set<TemplateVariable> NOT_AVAILABLE_ON_MOVE = EnumSet.of(TemplateVariable.INTERVIEW_DATE, TemplateVariable.INTERVIEW_LOCATION,
            TemplateVariable.POSITION, TemplateVariable.SALARY, TemplateVariable.START_DATE, TemplateVariable.BENEFITS);

    private static boolean usesUnavailableVariables(MessageTemplateResponse template) {
        return Stream.of(template.subject(), template.body())
                .filter(Objects::nonNull)
                .flatMap(text -> TemplateValidator.variablesIn(text).stream())
                .map(TemplateVariable::fromPlaceholder)
                .flatMap(Optional::stream)
                .anyMatch(NOT_AVAILABLE_ON_MOVE::contains);
    }

    private Map<UUID, MessageTemplateResponse> activeTemplates() {
        return messageTemplates.listActive(null).stream().collect(Collectors.toMap(MessageTemplateResponse::id, Function.identity()));
    }

    private static PipelineStageResponse toResponse(PipelineStage stage, Map<UUID, MessageTemplateResponse> templates) {
        MessageTemplateResponse template = stage.getAutoActionTemplateId() == null ? null : templates.get(stage.getAutoActionTemplateId());
        StageAutoActionResponse action = template == null
                ? new StageAutoActionResponse(StageAutoActionType.NONE, null, null)
                : new StageAutoActionResponse(StageAutoActionType.SEND_MESSAGE, template.id(), template.channel());
        return new PipelineStageResponse(stage.getId(), stage.getStageKey(), stage.getName(), stage.getPosition(), stage.getColor(), action);
    }
}
