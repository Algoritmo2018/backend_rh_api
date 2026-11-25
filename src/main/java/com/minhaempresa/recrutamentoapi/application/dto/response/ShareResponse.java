package com.minhaempresa.recrutamentoapi.application.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Result of sharing with the client.
 *
 * @param shared applications now shared
 * @param refused applications not shared because the consent the candidate gave does not cover sharing with the
 *     client company, with the reason (the others of the batch were shared)
 */
public record ShareResponse(int shared, List<Refusal> refused) {

    public record Refusal(UUID id, String message) {}
}
