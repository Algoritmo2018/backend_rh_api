package com.minhaempresa.recrutamentoapi.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * {@code POST /interviews/{id}/choose-slot} (staff "Fixar"; the portals in stage 9c): one of the proposed slots.
 *
 * @param ignoreConflicts staff only: fix it even if an interviewer or the candidate is busy at that time
 */
public record ChooseSlotRequest(@NotNull(message = "{validation.required}") Instant slot, Boolean ignoreConflicts) {}
