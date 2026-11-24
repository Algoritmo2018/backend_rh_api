package com.minhaempresa.recrutamentoapi.notification.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * "Enviar email de teste" with the data of the form, before saving.
 *
 * @param accountId saved account being edited: a blank password means its stored password
 */
public record EmailAccountTestRequest(UUID accountId, @Valid @NotNull(message = "{validation.required}") EmailAccountRequest account) {}
