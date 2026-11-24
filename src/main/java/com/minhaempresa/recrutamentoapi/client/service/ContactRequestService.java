package com.minhaempresa.recrutamentoapi.client.service;

import com.minhaempresa.recrutamentoapi.auth.service.RateLimiter;
import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestCreateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestFilterRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestStatusRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.ContactRequestResponse;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequest;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.event.ContactRequestReceivedEvent;
import com.minhaempresa.recrutamentoapi.client.mapper.ContactRequestMapper;
import com.minhaempresa.recrutamentoapi.client.repository.ContactRequestRepository;
import com.minhaempresa.recrutamentoapi.client.repository.ContactRequestSpecifications;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public contact forms ("Precisa de recrutar?" and Contacto) and their follow-up by the administrators.
 *
 * <p>Protection: a honeypot field (filled only by robots: the request is discarded silently), a
 * generous per-IP limit (CGNAT) and a strict per-email limit. The IP is used only by the in-memory
 * limiter, never stored. Personal data is kept only to answer the request (Lei n.º 22/11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactRequestService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final ContactRequestRepository repository;
    private final ContactRequestMapper mapper;
    private final RateLimiter rateLimiter;
    private final ContactRequestProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Always answers the same way (the controller returns 204), whether the request was kept or not. */
    @Transactional
    public void submit(ContactRequestCreateRequest request, String ipAddress) {
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Contact form honeypot filled: request discarded");
            return;
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        rateLimiter.check("contact-per-ip", ipAddress, properties.rateLimits().perIp());
        rateLimiter.check("contact-per-email", email, properties.rateLimits().perEmail());

        boolean company = request.kind() == ContactRequestKind.COMPANY;
        ContactRequest contactRequest = repository.save(new ContactRequest(
                request.kind(),
                request.name().trim(),
                company ? request.company().trim() : null,
                email,
                PhoneNumbers.normalizeOrThrow(request.phone()),
                request.message().trim(),
                company ? request.vacancies() : null));
        events.publishEvent(new ContactRequestReceivedEvent(
                contactRequest.getId(),
                contactRequest.getKind(),
                contactRequest.getName(),
                contactRequest.getCompany(),
                contactRequest.getEmail(),
                contactRequest.getPhone(),
                contactRequest.getMessage(),
                contactRequest.getVacancies()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactRequestResponse> list(ContactRequestFilterRequest filter, PageQuery page) {
        return PageResponse.from(
                repository.findAll(
                        ContactRequestSpecifications.kind(filter.kind()).and(ContactRequestSpecifications.status(filter.status())),
                        page.toPageable(NEWEST_FIRST)),
                mapper::toResponse);
    }

    /** Closing starts the retention period (deleted 12 months later, stage 15). */
    @Transactional
    public ContactRequestResponse changeStatus(UUID id, ContactRequestStatusRequest request) {
        ContactRequest contactRequest = repository.findById(id).orElseThrow(() -> new NotFoundException("contact-request.not-found"));
        if (request.status() != contactRequest.getStatus()) {
            contactRequest.changeStatus(request.status(), clock.instant());
        }
        return mapper.toResponse(contactRequest);
    }
}
