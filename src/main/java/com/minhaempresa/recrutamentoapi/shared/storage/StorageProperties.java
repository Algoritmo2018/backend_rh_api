package com.minhaempresa.recrutamentoapi.shared.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** File storage ({@code app.storage.*}). */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@Valid @NotNull Local local) {

    /** @param root folder where files are written; every key must resolve inside it */
    public record Local(@NotBlank String root) {}
}
