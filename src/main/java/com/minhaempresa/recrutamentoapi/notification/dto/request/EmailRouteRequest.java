package com.minhaempresa.recrutamentoapi.notification.dto.request;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Account of one kind of email.
 *
 * @param accountId {@code null} = the default server of the application
 */
public record EmailRouteRequest(@NotNull(message = "{validation.required}") EmailPurpose purpose, UUID accountId) {}
