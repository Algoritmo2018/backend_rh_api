package com.minhaempresa.recrutamentoapi.client.dto.response;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** Frontend {@code ContactRequest}; {@code phone} in E.164. Administrators only (personal data). */
public record ContactRequestResponse(
        UUID id,
        ContactRequestKind kind,
        String name,
        String company,
        String email,
        String phone,
        String message,
        Integer vacancies,
        ContactRequestStatus status,
        Instant createdAt) {}
