package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestStatus;
import jakarta.validation.constraints.NotNull;

/** Follow-up state of a contact request ({@code new}, {@code contacted}, {@code closed}). */
public record ContactRequestStatusRequest(@NotNull(message = "{validation.required}") ContactRequestStatus status) {}
