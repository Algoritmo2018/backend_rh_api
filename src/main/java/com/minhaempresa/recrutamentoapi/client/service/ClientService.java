package com.minhaempresa.recrutamentoapi.client.service;

import com.minhaempresa.recrutamentoapi.client.dto.request.ClientContactRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ClientCreateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ClientUpdateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientOptionResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientPortalCompanyResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientResponse;
import com.minhaempresa.recrutamentoapi.client.entity.Client;
import com.minhaempresa.recrutamentoapi.client.entity.ClientContact;
import com.minhaempresa.recrutamentoapi.client.entity.ClientStatus;
import com.minhaempresa.recrutamentoapi.client.event.ClientDeactivatedEvent;
import com.minhaempresa.recrutamentoapi.client.event.ClientUpdatedEvent;
import com.minhaempresa.recrutamentoapi.client.mapper.ClientMapper;
import com.minhaempresa.recrutamentoapi.client.repository.ClientRepository;
import com.minhaempresa.recrutamentoapi.client.service.ClientStatsContributor.ClientCounts;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client companies (Clientes) and their contacts. Clients are never deleted: deactivating one ends the
 * access of its portal users ({@link ClientDeactivatedEvent}); reactivating gives it back (except to
 * users deactivated on their own). The company does not charge fees to clients.
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private static final Sort BY_NAME = Sort.by("name");

    private final ClientRepository repository;
    private final ClientMapper mapper;
    private final List<ClientStatsContributor> statsContributors;
    private final ApplicationEventPublisher events;

    // =====================================================================================
    // Administration
    // =====================================================================================

    /** Every client with contacts and numbers (administrators: contains personal data). */
    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        List<Client> clients = repository.findAllWithContacts(BY_NAME);
        Map<UUID, ClientCounts> counts = counts(clients.stream().map(Client::getId).toList());
        return clients.stream().map(c -> toResponse(c, counts)).toList();
    }

    /** Minimal list for pickers (recruiters too): no contacts. */
    @Transactional(readOnly = true)
    public List<ClientOptionResponse> listOptions() {
        return repository.findAll(BY_NAME).stream().map(mapper::toOption).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        Client client = find(id);
        return toResponse(client, counts(List.of(id)));
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request) {
        String nif = request.nif().trim();
        if (repository.existsByNif(nif)) {
            throw nifTaken();
        }
        Client client = new Client(request.name().trim(), request.sector(), nif, request.province(), request.city().trim(), request.address().trim());
        client.setWebsite(blankToNull(request.website()));
        client.setNotes(blankToNull(request.notes()));
        client.setStatus(request.status() == null ? ClientStatus.ACTIVE : request.status());
        repository.save(client);
        replaceContacts(client, request.contacts());
        return toResponse(client, counts(List.of(client.getId())));
    }

    /** Partial update; {@code contacts}, when sent, replaces the whole list. */
    @Transactional
    public ClientResponse update(UUID id, ClientUpdateRequest request) {
        Client client = find(id);
        if (request.name() != null) {
            client.setName(request.name().trim());
        }
        if (request.sector() != null) {
            client.setSector(request.sector());
        }
        if (request.nif() != null) {
            String nif = request.nif().trim();
            if (repository.existsByNifAndIdNot(nif, id)) {
                throw nifTaken();
            }
            client.setNif(nif);
        }
        if (request.province() != null) {
            client.setProvince(request.province());
        }
        if (request.city() != null) {
            client.setCity(request.city().trim());
        }
        if (request.address() != null) {
            client.setAddress(request.address().trim());
        }
        if (request.website() != null) {
            client.setWebsite(blankToNull(request.website()));
        }
        if (request.notes() != null) {
            client.setNotes(blankToNull(request.notes()));
        }
        if (request.status() != null && request.status() != client.getStatus()) {
            client.setStatus(request.status());
            if (request.status() == ClientStatus.INACTIVE) {
                events.publishEvent(new ClientDeactivatedEvent(client.getId()));
            }
        }
        if (request.contacts() != null) {
            replaceContacts(client, request.contacts());
        }
        events.publishEvent(new ClientUpdatedEvent(client.getId()));
        return toResponse(client, counts(List.of(id)));
    }

    // =====================================================================================
    // Support for other modules (never exposed by a controller)
    // =====================================================================================

    /** Whether the client exists and is active (client users may only belong to active clients). */
    @Transactional(readOnly = true)
    public boolean isActive(UUID clientId) {
        return clientId != null && repository.existsByIdAndStatus(clientId, ClientStatus.ACTIVE);
    }

    /** Names of the given clients (missing ids are absent). */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdIn(Set.copyOf(ids)).stream().collect(Collectors.toMap(Client::getId, Client::getName));
    }

    /** Ids of the clients whose name contains the term (case and accents ignored). */
    @Transactional(readOnly = true)
    public List<UUID> idsWithNameContaining(String term) {
        String escaped = term.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return repository.findIdsByNameContaining(escaped);
    }

    /**
     * The active company of a client-portal user, from the access token only. An inactive company →
     * 403: the old access token may still be valid for a few minutes after a deactivation.
     */
    @Transactional(readOnly = true)
    public ClientPortalCompanyResponse requireActivePortalClient(AuthenticatedUser actor) {
        if (actor.clientId() == null) {
            throw new ForbiddenException();
        }
        Client client = repository.findById(actor.clientId()).orElseThrow(ForbiddenException::new);
        if (!client.isActive()) {
            throw new ForbiddenException("client.inactive");
        }
        return new ClientPortalCompanyResponse(client.getId(), client.getName());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Applies the full contact list: a UUID of an existing contact of this client updates it, anything
     * else creates a contact, and existing contacts missing from the list are deleted. Exactly one
     * contact is primary (the marked one, or the first).
     *
     * <p>Two steps, because the database allows one primary per client at any moment: first every
     * contact is saved as non-primary, then the primary is set.
     */
    private void replaceContacts(Client client, List<ClientContactRequest> items) {
        if (items.stream().filter(ClientContactRequest::isPrimary).count() > 1) {
            throw new BusinessException("client.contacts.one-primary", List.of(new FieldError("contacts", "client.contacts.one-primary")));
        }
        Map<UUID, ClientContact> existing = new HashMap<>();
        client.getContacts().forEach(c -> existing.put(c.getId(), c));

        List<ClientContact> kept = new ArrayList<>();
        ClientContact primary = null;
        for (ClientContactRequest item : items) {
            UUID id = parseUuid(item.id());
            ClientContact contact = id == null ? null : existing.remove(id);
            String email = item.email().trim().toLowerCase(Locale.ROOT);
            String phone = PhoneNumbers.normalizeOrThrow(item.phone());
            if (contact == null) {
                contact = client.addContact(item.name().trim(), item.role().trim(), email, phone);
            } else {
                contact.setName(item.name().trim());
                contact.setRole(item.role().trim());
                contact.setEmail(email);
                contact.setPhone(phone);
            }
            contact.setPrimary(false);
            kept.add(contact);
            if (item.isPrimary()) {
                primary = contact;
            }
        }
        client.getContacts().removeAll(existing.values());
        // The client is managed: flushing persists new contacts (cascade) and deletes orphans. No
        // save/merge here, which would copy the new contacts and leave these instances detached.
        repository.flush();

        if (primary == null && !kept.isEmpty()) {
            primary = kept.getFirst();
        }
        if (primary != null) {
            primary.setPrimary(true);
        }
    }

    private ClientResponse toResponse(Client client, Map<UUID, ClientCounts> counts) {
        return mapper.toResponse(client, counts.getOrDefault(client.getId(), ClientCounts.NONE));
    }

    private Map<UUID, ClientCounts> counts(List<UUID> ids) {
        Map<UUID, ClientCounts> total = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return total;
        }
        for (ClientStatsContributor contributor : statsContributors) {
            contributor.countsByClient(ids).forEach((id, counts) -> total.merge(id, counts, ClientCounts::plus));
        }
        return total;
    }

    private Client find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("client.not-found"));
    }

    private static ConflictException nifTaken() {
        return new ConflictException("client.nif-taken", List.of(new FieldError("nif", "client.nif-taken")));
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
