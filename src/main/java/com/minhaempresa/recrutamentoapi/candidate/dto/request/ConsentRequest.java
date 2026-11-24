package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import jakarta.validation.constraints.NotNull;

/** Grant or revoke a general consent ({@code PUT /me/consents/{type}}). */
public record ConsentRequest(@NotNull(message = "{validation.required}") Boolean granted) {}
