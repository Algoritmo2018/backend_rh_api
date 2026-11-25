package com.minhaempresa.recrutamentoapi.application.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /applications/share-with-client} (frontend {@code shareWithClient(ids, summary)}).
 *
 * @param summary recruiter summary for every shared application (visible to the client); blank keeps each one
 */
public record ShareWithClientRequest(
        @NotEmpty(message = "{validation.application.ids}") List<@NotNull(message = "{validation.required}") UUID> ids,
        @Size(max = 2000, message = "{validation.application.summary}") String summary) {}
