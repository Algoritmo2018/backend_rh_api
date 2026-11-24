package com.minhaempresa.recrutamentoapi.notification.dto.response;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import java.util.UUID;

/**
 * Account that sends a kind of email.
 *
 * @param accountId {@code null} = the default server of the application ({@code spring.mail})
 */
public record EmailRouteResponse(EmailPurpose purpose, UUID accountId, String accountName) {}
