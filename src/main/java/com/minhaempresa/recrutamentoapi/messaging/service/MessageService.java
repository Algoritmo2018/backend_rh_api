package com.minhaempresa.recrutamentoapi.messaging.service;

import com.minhaempresa.recrutamentoapi.candidate.entity.ConsentType;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateContact;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.ConsentService;
import com.minhaempresa.recrutamentoapi.messaging.dto.request.MessageFilterRequest;
import com.minhaempresa.recrutamentoapi.messaging.dto.request.MessageSendRequest;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.ConversationResponse;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessagePreviewResponse;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageResponse;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageWithRefsResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.Message;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageTemplate;
import com.minhaempresa.recrutamentoapi.messaging.event.MessageDispatchEvent;
import com.minhaempresa.recrutamentoapi.messaging.mapper.MessageMapper;
import com.minhaempresa.recrutamentoapi.messaging.repository.MessageRepository;
import com.minhaempresa.recrutamentoapi.messaging.repository.MessageRepository.ConversationRow;
import com.minhaempresa.recrutamentoapi.messaging.repository.MessageSpecifications;
import com.minhaempresa.recrutamentoapi.messaging.repository.MessageTemplateRepository;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateValidator;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateVariable;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer;
import com.minhaempresa.recrutamentoapi.notification.email.EmailTemplateRenderer.RenderedEmail;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.sender.MailHeaders;
import com.minhaempresa.recrutamentoapi.notification.sender.EmailAttachment;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.exception.TooManyRequestsException;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import com.minhaempresa.recrutamentoapi.user.service.UserReference;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageApplicationDirectory.ApplicationRef;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messages to candidates (page Mensagens, candidate profile, and the automatic actions of the pipeline
 * from stage 8).
 *
 * <ul>
 *   <li>Every message is a snapshot: final subject and body plus the source template code.
 *   <li>Plain text only. Variables are filled on the server; their values are untrusted data. The email
 *       subject loses its line breaks ({@link MailHeaders}); the HTML version escapes everything.
 *   <li>Email goes through the outbox (purpose {@code recruitment}); SMS and WhatsApp are simulated for now
 *       (log), and WhatsApp falls back to SMS when the candidate has no WhatsApp number.
 *   <li>Without an application, the candidate must have an active talent-pool consent; a marketing message
 *       needs the marketing consent (decision D7-6).
 *   <li>Limits of manual sends (D7-7): per sender per hour and per day, per candidate per day; 429 with
 *       {@code Retry-After}. Counted in the database, so they hold with several instances.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    /** Candidates matched by name when searching the inbox. */
    private static final int NAME_SEARCH_LIMIT = 200;

    private final MessageRepository repository;
    private final MessageTemplateRepository templates;
    private final MessageMapper mapper;
    private final TemplateRenderer templateRenderer;
    private final EmailTemplateRenderer emailRenderer;
    private final EmailOutbox outbox;
    private final CandidateService candidateService;
    private final ConsentService consentService;
    private final UserService userService;
    private final ObjectProvider<MessageApplicationDirectory> applicationDirectory;
    private final ApplicationEventPublisher events;
    private final MessagingProperties properties;
    private final AppProperties appProperties;
    private final Messages messages;
    private final Clock clock;

    // =====================================================================================
    // Sending
    // =====================================================================================

    /** A message written (or chosen) by a recruiter. */
    @Transactional
    public MessageResponse send(MessageSendRequest request, AuthenticatedUser actor) {
        MessageTemplate template = request.templateId() == null ? null : activeTemplate(request.templateId());
        if (template != null && template.getChannel() != request.channel()) {
            throw new BusinessException("message.template.channel-mismatch", List.of(new FieldError("templateId", "message.template.channel-mismatch")));
        }
        String body = Texts.blankToNull(request.body());
        if (body == null) {
            if (template == null) {
                throw new BusinessException("message.body.required", List.of(new FieldError("body", "message.body.required")));
            }
            body = template.getBody();
        }
        String subject = null;
        if (request.channel() == MessageChannel.EMAIL) {
            subject = Texts.blankToNull(request.subject());
            if (subject == null && template != null) {
                subject = template.getSubject();
            }
        }
        List<FieldError> errors = TemplateValidator.validateMessageTemplate(request.channel(), subject, body);
        if (!errors.isEmpty()) {
            throw new BusinessException("error.validation", errors);
        }
        boolean marketing = Boolean.TRUE.equals(request.marketing());
        CandidateContact candidate = candidateService.getContact(request.candidateId());
        Map<TemplateVariable, String> jobValues = applicationValues(request.applicationId(), candidate.id());
        requireConsent(candidate.id(), request.applicationId(), marketing);
        checkLimits(actor.id(), candidate.id());
        return deliver(candidate, request.applicationId(), request.channel(), subject, body, template, marketing, actor.id(), false,
                userService.getById(actor.id()).name(), jobValues, null);
    }

    /**
     * Sends an active template to a candidate: the entry point of the automatic actions of the pipeline
     * (stage 8) and of other modules.
     *
     * @param applicationId the application the message is about: it must belong to the candidate, gives the
     *     consent to contact them and the job variables ({@code {{vaga}}}, {@code {{empresa}}}); {@code null}
     *     = a talent-pool message
     * @param channel {@code null} = the template's channel
     * @param extraValues values of variables this module cannot know (interview…); they win over the job ones
     * @param sentBy the user who caused it (also for automatic sends); {@code null} only when no user did
     *     (e.g. a public application). A failed email is reported to this user, or to the administrators
     * @param automatic triggered by the system (stage automatic action, bulk action): not counted in the
     *     sending limits (stage 8, adjustment 1)
     */
    @Transactional
    public MessageResponse sendFromTemplate(UUID templateId, UUID candidateId, UUID applicationId, MessageChannel channel,
            Map<TemplateVariable, String> extraValues, UUID sentBy, boolean automatic) {
        return sendFromTemplate(templateId, candidateId, applicationId, channel, extraValues, sentBy, automatic, null);
    }

    /**
     * Same as {@link #sendFromTemplate(UUID, UUID, UUID, MessageChannel, Map, UUID, boolean)} with an attachment for an
     * email (e.g. the {@code .ics} of an interview, stage 9); ignored for SMS/WhatsApp.
     */
    @Transactional
    public MessageResponse sendFromTemplate(UUID templateId, UUID candidateId, UUID applicationId, MessageChannel channel,
            Map<TemplateVariable, String> extraValues, UUID sentBy, boolean automatic, EmailAttachment attachment) {
        MessageTemplate template = activeTemplate(templateId);
        CandidateContact candidate = candidateService.getContact(candidateId);
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.putAll(applicationValues(applicationId, candidate.id()));
        values.putAll(extraValues);
        requireConsent(candidate.id(), applicationId, false);
        if (sentBy != null && !automatic) {
            checkLimits(sentBy, candidate.id());
        }
        String senderName = sentBy == null ? null : userService.getById(sentBy).name();
        MessageChannel target = channel == null ? template.getChannel() : channel;
        String subject = target == MessageChannel.EMAIL ? template.getSubject() : null;
        return deliver(candidate, applicationId, target, subject, template.getBody(), template, false, sentBy, automatic, senderName, values, attachment);
    }

    /** Fills the template for one candidate without sending it (variables without a value stay visible). */
    @Transactional(readOnly = true)
    public MessagePreviewResponse preview(UUID templateId, UUID candidateId, UUID applicationId, AuthenticatedUser actor) {
        MessageTemplate template = templates.findById(templateId).filter(MessageTemplate::isActive)
                .orElseThrow(() -> new NotFoundException("template.not-found"));
        CandidateContact candidate = candidateService.getContact(candidateId);
        Map<TemplateVariable, String> values = values(candidate, userService.getById(actor.id()).name(), applicationValues(applicationId, candidate.id()));
        String subject = template.getSubject() == null ? null : MailHeaders.clean(templateRenderer.renderText(template.getSubject(), values));
        return new MessagePreviewResponse(template.getChannel(), subject, templateRenderer.renderText(template.getBody(), values));
    }

    /** Delivery result of an email (outbox) or of an SMS/WhatsApp message. Unknown ids are ignored. */
    @Transactional
    public void applyDeliveryResult(UUID messageId, boolean sent, String error) {
        repository.findById(messageId).ifPresent(message -> {
            if (sent) {
                message.markSent();
            } else {
                message.markFailed(error);
            }
        });
    }

    // =====================================================================================
    // Inbox
    // =====================================================================================

    @Transactional(readOnly = true)
    public PageResponse<MessageWithRefsResponse> list(MessageFilterRequest filter, PageQuery pageQuery) {
        String search = Texts.blankToNull(filter.search());
        search = search == null ? null : search.trim();
        List<UUID> named = search == null ? List.of() : candidateService.idsByName(search, NAME_SEARCH_LIMIT);
        Page<Message> page = repository.findAll(
                MessageSpecifications.matching(filter.candidateId(), filter.applicationId(), filter.channel(), filter.direction(), search, named),
                pageQuery.toPageable(NEWEST_FIRST));
        Function<Message, MessageWithRefsResponse> toRefs = withRefs(page.getContent());
        return PageResponse.from(page, toRefs);
    }

    /** One entry per candidate, the most recent conversation first. */
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> conversations(PageQuery pageQuery) {
        Page<ConversationRow> rows = repository.findConversations(pageQuery.toPageable());
        List<UUID> candidateIds = rows.getContent().stream().map(ConversationRow::getCandidateId).toList();
        List<Message> latest = candidateIds.isEmpty() ? List.of() : repository.findLatestOf(candidateIds);
        Map<UUID, Message> latestByCandidate = latest.stream().collect(Collectors.toMap(Message::getCandidateId, m -> m));
        Function<Message, MessageWithRefsResponse> toRefs = withRefs(latest);
        return PageResponse.from(rows, row -> {
            MessageWithRefsResponse last = toRefs.apply(latestByCandidate.get(row.getCandidateId()));
            return new ConversationResponse(row.getCandidateId(), last.candidateName(), last.candidateHasWhatsapp(), row.getTotal(), last);
        });
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private MessageResponse deliver(CandidateContact candidate, UUID applicationId, MessageChannel requested, String subjectSource,
            String bodySource, MessageTemplate template, boolean marketing, UUID sentBy, boolean automatic, String senderName,
            Map<TemplateVariable, String> extraValues, EmailAttachment attachment) {
        Map<TemplateVariable, String> values = values(candidate, senderName, extraValues);
        requireFilled("subject", subjectSource, values);
        requireFilled("body", bodySource, values);
        String subject = subjectSource == null ? null : MailHeaders.clean(templateRenderer.renderText(subjectSource, values));
        String body = templateRenderer.renderText(bodySource, values);

        MessageChannel channel = requested == MessageChannel.WHATSAPP && !candidate.hasWhatsapp() ? MessageChannel.SMS : requested;
        if (channel == MessageChannel.SMS && body.length() > TemplateValidator.SMS_MAX_LENGTH) {
            throw new BusinessException("message.sms-too-long", List.of(new FieldError("body", "message.sms-too-long", List.of(TemplateValidator.SMS_MAX_LENGTH))));
        }

        Message message = repository.save(new Message(candidate.id(), applicationId, channel, subject, body, template, marketing, sentBy, automatic, clock.instant()));
        switch (channel) {
            case EMAIL -> {
                RenderedEmail rendered = emailRenderer.renderMessage(subject, body);
                outbox.enqueue(EmailPurpose.RECRUITMENT, candidate.email(), subject, rendered.text(), rendered.html(), message.getId(), sentBy, attachment);
            }
            case SMS -> events.publishEvent(new MessageDispatchEvent(message.getId(), channel, candidate.phone(), body));
            case WHATSAPP -> events.publishEvent(new MessageDispatchEvent(message.getId(), channel, candidate.whatsapp(), body));
        }
        return mapper.toResponse(message);
    }

    /** Variables available now; the application ones (job, interview…) arrive with stage 8 as extra values. */
    private Map<TemplateVariable, String> values(CandidateContact candidate, String senderName, Map<TemplateVariable, String> extraValues) {
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.putAll(extraValues);
        values.put(TemplateVariable.FIRST_NAME, candidate.firstName());
        values.put(TemplateVariable.FULL_NAME, candidate.fullName());
        values.put(TemplateVariable.LINK, appProperties.candidatePortalUrl());
        values.put(TemplateVariable.RECRUITER, senderName == null ? messages.get("message.automatic-sender") : senderName);
        return values;
    }

    /**
     * A variable of the source text without a value would reach the candidate as {@code {{vaga}}}: refused.
     * Checked on the source, not on the result: a value (e.g. a name) containing braces is just text.
     */
    private static void requireFilled(String field, String source, Map<TemplateVariable, String> values) {
        if (source == null) {
            return;
        }
        TemplateValidator.variablesIn(source).stream()
                .filter(name -> TemplateVariable.fromPlaceholder(name).map(values::get).filter(v -> !v.isEmpty()).isEmpty())
                .findFirst()
                .ifPresent(name -> {
            throw new BusinessException("message.variable-unavailable", List.of(new FieldError(field, "message.variable-unavailable", List.of("{{" + name + "}}"))), "{{" + name + "}}");
        });
    }

    private MessageTemplate activeTemplate(UUID id) {
        return templates.findById(id).filter(MessageTemplate::isActive)
                .orElseThrow(() -> new BusinessException("message.template.invalid", List.of(new FieldError("templateId", "message.template.invalid"))));
    }

    /** The application must exist and belong to the candidate (422 on {@code applicationId} otherwise). */
    private ApplicationRef applicationOf(UUID applicationId, UUID candidateId) {
        return applicationDirectory.orderedStream()
                .map(directory -> directory.find(applicationId))
                .flatMap(Optional::stream)
                .filter(application -> application.candidateId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("message.application.invalid", List.of(new FieldError("applicationId", "message.application.invalid"))));
    }

    /**
     * Job variables of a message about an application ({{vaga}}, {{empresa}}: the neutral text for a confidential
     * job); empty without an application. The application must belong to the candidate (422 otherwise).
     */
    private Map<TemplateVariable, String> applicationValues(UUID applicationId, UUID candidateId) {
        if (applicationId == null) {
            return Map.of();
        }
        ApplicationRef application = applicationOf(applicationId, candidateId);
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.put(TemplateVariable.JOB_TITLE, application.jobTitle());
        values.put(TemplateVariable.COMPANY, application.companyName());
        return values;
    }

    /**
     * Without an application the candidate must be in the talent pool (active consent); marketing needs
     * the marketing consent.
     */
    private void requireConsent(UUID candidateId, UUID applicationId, boolean marketing) {
        if (applicationId == null && !consentService.hasActive(candidateId, ConsentType.TALENT_POOL)) {
            throw new BusinessException("message.consent.talent-pool", List.of(new FieldError("candidateId", "message.consent.talent-pool")));
        }
        if (marketing && !consentService.hasActive(candidateId, ConsentType.MARKETING)) {
            throw new BusinessException("message.consent.marketing", List.of(new FieldError("marketing", "message.consent.marketing")));
        }
    }

    private void checkLimits(UUID senderId, UUID candidateId) {
        Instant now = clock.instant();
        Duration hour = Duration.ofHours(1);
        Duration day = Duration.ofDays(1);
        limit(repository.countBySentByAndAutomaticFalseAndCreatedAtAfter(senderId, now.minus(hour)), properties.perSenderHour(),
                () -> repository.findFirstBySentByAndAutomaticFalseAndCreatedAtAfterOrderByCreatedAtAsc(senderId, now.minus(hour)), hour, now, "message.rate-limit.sender");
        limit(repository.countBySentByAndAutomaticFalseAndCreatedAtAfter(senderId, now.minus(day)), properties.perSenderDay(),
                () -> repository.findFirstBySentByAndAutomaticFalseAndCreatedAtAfterOrderByCreatedAtAsc(senderId, now.minus(day)), day, now, "message.rate-limit.sender");
        limit(repository.countByCandidateIdAndAutomaticFalseAndCreatedAtAfter(candidateId, now.minus(day)), properties.perCandidateDay(),
                () -> repository.findFirstByCandidateIdAndAutomaticFalseAndCreatedAtAfterOrderByCreatedAtAsc(candidateId, now.minus(day)), day, now, "message.rate-limit.candidate");
    }

    /** Over the limit → 429, retry when the oldest message of the window leaves it. */
    private static void limit(long count, int max, Supplier<Optional<Message>> oldest, Duration window, Instant now, String key) {
        if (count < max) {
            return;
        }
        Instant leaves = oldest.get().map(Message::getCreatedAt).orElse(now).plus(window);
        Duration retryAfter = Duration.between(now, leaves);
        throw new TooManyRequestsException(key, retryAfter.isNegative() || retryAfter.isZero() ? Duration.ofSeconds(1) : retryAfter);
    }

    /** Adds candidate and sender names and the job title of the application to a list of messages (one query each for the whole page). */
    private Function<Message, MessageWithRefsResponse> withRefs(Collection<Message> page) {
        Map<UUID, CandidateContact> candidates = candidateService.contactsOf(page.stream().map(Message::getCandidateId).distinct().toList());
        Map<UUID, UserReference> senders = userService.referencesOf(page.stream().map(Message::getSentBy).filter(Objects::nonNull).distinct().toList());
        List<UUID> applicationIds = page.stream().map(Message::getApplicationId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> jobTitles = new HashMap<>();
        if (!applicationIds.isEmpty()) {
            applicationDirectory.orderedStream().forEach(directory -> jobTitles.putAll(directory.jobTitlesOf(applicationIds)));
        }
        return message -> {
            CandidateContact candidate = candidates.get(message.getCandidateId());
            UserReference sender = message.getSentBy() == null ? null : senders.get(message.getSentBy());
            return MessageWithRefsResponse.of(mapper.toResponse(message),
                    candidate == null ? null : candidate.fullName(),
                    candidate != null && candidate.hasWhatsapp(),
                    message.getApplicationId() == null ? null : jobTitles.get(message.getApplicationId()),
                    sender == null ? null : sender.name());
        };
    }
}
