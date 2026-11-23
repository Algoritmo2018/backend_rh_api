package com.minhaempresa.recrutamentoapi.settings.dto.response;

import com.minhaempresa.recrutamentoapi.settings.entity.RejectionReasonCategory;
import java.util.UUID;

/** Frontend {@code RejectionReason} plus {@code code}. */
public record RejectionReasonResponse(UUID id, String code, String label, RejectionReasonCategory category, boolean active, int order) {}
