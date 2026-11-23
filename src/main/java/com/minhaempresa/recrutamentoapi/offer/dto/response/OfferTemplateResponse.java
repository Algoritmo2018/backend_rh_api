package com.minhaempresa.recrutamentoapi.offer.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Frontend {@code OfferTemplate} plus {@code code}. */
public record OfferTemplateResponse(UUID id, String code, String name, String body, boolean isDefault, Instant updatedAt) {}
