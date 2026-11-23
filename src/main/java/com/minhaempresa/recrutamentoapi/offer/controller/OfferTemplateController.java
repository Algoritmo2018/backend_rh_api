package com.minhaempresa.recrutamentoapi.offer.controller;

import com.minhaempresa.recrutamentoapi.offer.dto.request.OfferTemplateRequest;
import com.minhaempresa.recrutamentoapi.offer.dto.response.OfferTemplateResponse;
import com.minhaempresa.recrutamentoapi.offer.service.OfferTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Offer templates: read by admin and recruiter (offer form), edited by admin (settings). */
@RestController
@RequestMapping("/api/v1/offer-templates")
@RequiredArgsConstructor
public class OfferTemplateController {

    private final OfferTemplateService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public List<OfferTemplateResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public OfferTemplateResponse create(@Valid @RequestBody OfferTemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OfferTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody OfferTemplateRequest request) {
        return service.update(id, request);
    }
}
