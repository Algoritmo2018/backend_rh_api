package com.minhaempresa.recrutamentoapi.settings.dto.response;

import com.minhaempresa.recrutamentoapi.settings.entity.TagColor;
import java.util.UUID;

/** Frontend {@code Tag}. */
public record TagResponse(UUID id, String name, TagColor color) {}
