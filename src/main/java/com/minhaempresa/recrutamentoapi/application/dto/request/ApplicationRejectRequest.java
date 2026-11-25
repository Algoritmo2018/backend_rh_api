package com.minhaempresa.recrutamentoapi.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /applications/reject} (frontend {@code RejectOptions}; the user comes from the token).
 *
 * @param reasonId an active rejection reason
 * @param notifyCandidates JSON {@code notify} ({@code notify} cannot name a record component): send the "Não seleção" email ({@code rejection-email}), one email per candidate
 */
public record ApplicationRejectRequest(
        @NotEmpty(message = "{validation.application.ids}") List<@NotNull(message = "{validation.required}") UUID> ids,
        @NotNull(message = "{validation.required}") UUID reasonId,
        @Size(max = 500, message = "{validation.application.note}") String note,
        @JsonProperty("notify") Boolean notifyCandidates) {}
