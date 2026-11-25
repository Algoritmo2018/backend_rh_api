package com.minhaempresa.recrutamentoapi.application.dto.response;

import java.util.UUID;

/** Application of a logged-in candidate (the candidate portal views arrive in stage 12). */
public record ApplicationCreatedResponse(UUID applicationId) {}
