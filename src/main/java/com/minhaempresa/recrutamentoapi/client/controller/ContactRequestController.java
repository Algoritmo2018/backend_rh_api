package com.minhaempresa.recrutamentoapi.client.controller;

import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestCreateRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestFilterRequest;
import com.minhaempresa.recrutamentoapi.client.dto.request.ContactRequestStatusRequest;
import com.minhaempresa.recrutamentoapi.client.dto.response.ContactRequestResponse;
import com.minhaempresa.recrutamentoapi.client.service.ContactRequestService;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public contact forms (no login) and their follow-up by the administrators. There is no frontend
 * screen for the list yet (pending for the integration stage).
 */
@RestController
@RequiredArgsConstructor
public class ContactRequestController {

    private final ContactRequestService contactRequestService;

    /** Always 204: a discarded request (honeypot) looks exactly like an accepted one. */
    @PostMapping("/api/v1/public/contact-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@Valid @RequestBody ContactRequestCreateRequest request, HttpServletRequest http) {
        contactRequestService.submit(request, http.getRemoteAddr());
    }

    @GetMapping("/api/v1/contact-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ContactRequestResponse> list(@Valid ContactRequestFilterRequest filter, @Valid PageQuery page) {
        return contactRequestService.list(filter, page);
    }

    @PatchMapping("/api/v1/contact-requests/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ContactRequestResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ContactRequestStatusRequest request) {
        return contactRequestService.changeStatus(id, request);
    }
}
