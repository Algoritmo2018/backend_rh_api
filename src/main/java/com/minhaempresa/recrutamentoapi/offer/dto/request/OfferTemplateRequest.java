package com.minhaempresa.recrutamentoapi.offer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or edit an offer template (Configurações → Modelos de proposta). Plain text only. */
public record OfferTemplateRequest(
        @NotBlank(message = "{validation.required}") @Size(min = 3, max = 80, message = "{template.name.size}") String name,
        @NotBlank(message = "{validation.required}") @Size(min = 20, max = 8000, message = "{offer-template.body.size}") String body,
        @NotNull(message = "{validation.required}") Boolean isDefault) {}
