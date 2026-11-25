package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.application.entity.ClientDecision;
import com.minhaempresa.recrutamentoapi.shared.domain.PipelineStageKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A shared application in the client portal (stage 8c). Only the approved fields: no rating, tags, matches,
 * answers, source, history, rejection data, contacts nor the original CV.
 *
 * @param id the application
 * @param score 0–100, {@code null} = "sem score"
 * @param quickProfile sent with the application instead of a CV, or {@code null}
 * @param clientCvUrl API path of the "CV para o cliente" (fetch with the token), or {@code null}: no file then
 */
public record ClientSharedApplicationResponse(
        UUID id,
        UUID jobId,
        PipelineStageKey stage,
        String stageName,
        Integer score,
        Instant sharedAt,
        ClientDecision clientDecision,
        String recruiterSummary,
        List<ClientCommentView> clientComments,
        ClientSharedCandidateResponse candidate,
        QuickProfileResponse quickProfile,
        String clientCvUrl) {}
