package com.minhaempresa.recrutamentoapi.client.dto.response;

import java.util.UUID;

/** Frontend {@code ClientContact}; {@code phone} in E.164. */
public record ClientContactResponse(UUID id, String name, String role, String email, String phone, boolean isPrimary) {}
