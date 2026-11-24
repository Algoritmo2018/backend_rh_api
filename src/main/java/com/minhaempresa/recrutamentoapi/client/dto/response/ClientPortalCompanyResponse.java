package com.minhaempresa.recrutamentoapi.client.dto.response;

import java.util.UUID;

/** Company of the logged-in client user (client portal header). */
public record ClientPortalCompanyResponse(UUID id, String name) {}
