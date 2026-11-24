package com.minhaempresa.recrutamentoapi.client.controller;

import com.minhaempresa.recrutamentoapi.client.dto.request.ClientCreateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ClientUpdateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientOptionResponse;
import com.minhaempresa.recrutamentoapi.client.dto.response.ClientResponse;
import com.minhaempresa.recrutamentoapi.client.service.ClientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client companies (Clientes). The full record has personal data of the contacts: administrators only.
 * Recruiters get the minimal list for pickers ({@code /options}).
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ClientResponse> list() {
        return clientService.list();
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<ClientOptionResponse> options() {
        return clientService.listOptions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse get(@PathVariable UUID id) {
        return clientService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse create(@Valid @RequestBody ClientCreateRequest request) {
        return clientService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientUpdateRequest request) {
        return clientService.update(id, request);
    }
}
