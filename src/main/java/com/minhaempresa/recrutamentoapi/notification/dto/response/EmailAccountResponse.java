package com.minhaempresa.recrutamentoapi.notification.dto.response;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailSecurity;
import java.time.Instant;
import java.util.UUID;

/**
 * SMTP account as shown in the settings. The password is never returned.
 *
 * @param hasPassword a password is stored (leave the field blank to keep it)
 */
public record EmailAccountResponse(
        UUID id,
        String name,
        String host,
        int port,
        EmailSecurity security,
        String username,
        boolean hasPassword,
        String fromAddress,
        String fromName,
        String replyTo,
        boolean active,
        Instant updatedAt) {}
