package com.minhaempresa.recrutamentoapi.settings.dto.request;

import com.minhaempresa.recrutamentoapi.settings.entity.TagColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or edit a tag (frontend {@code saveTag}). */
public record TagRequest(
        @NotBlank(message = "{settings.tag.name-required}") @Size(max = 40, message = "{settings.tag.name-size}") String name,
        @NotNull(message = "{validation.required}") TagColor color) {}
