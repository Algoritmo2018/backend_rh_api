package com.minhaempresa.recrutamentoapi.client.dto.response;

import com.minhaempresa.recrutamentoapi.client.entity.ClientSector;
import com.minhaempresa.recrutamentoapi.client.entity.ClientStatus;
import java.util.UUID;

/** Minimal client for pickers (job form, user form, filters): no contacts, no personal data. */
public record ClientOptionResponse(UUID id, String name, ClientSector sector, ClientStatus status) {}
