package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestStatus;

/** Query parameters of {@code GET /contact-requests}. */
public record ContactRequestFilterRequest(ContactRequestKind kind, ContactRequestStatus status) {}
