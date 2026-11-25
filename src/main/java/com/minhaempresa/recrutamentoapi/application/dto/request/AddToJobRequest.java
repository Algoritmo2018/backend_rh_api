package com.minhaempresa.recrutamentoapi.application.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** {@code POST /talent-pool/add-to-job}: talent-pool candidates into an open job (stage "Triagem", D8-7). */
public record AddToJobRequest(
        @NotEmpty(message = "{validation.application.candidates}") List<@NotNull(message = "{validation.required}") UUID> candidateIds,
        @NotNull(message = "{validation.required}") UUID jobId) {}
