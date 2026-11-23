package com.minhaempresa.recrutamentoapi.user.dto.request;

import jakarta.validation.constraints.NotNull;

/** Activate or deactivate an account. Deactivating revokes its sessions immediately. */
public record UserStatusRequest(@NotNull(message = "{validation.required}") Boolean active) {}
