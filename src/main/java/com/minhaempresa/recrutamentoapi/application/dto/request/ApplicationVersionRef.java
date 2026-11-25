package com.minhaempresa.recrutamentoapi.application.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** An application and the version read with it (bulk move). */
public record ApplicationVersionRef(
        @NotNull(message = "{validation.required}") UUID id,
        @NotNull(message = "{validation.required}") Long version) {}
