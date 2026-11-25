package com.minhaempresa.recrutamentoapi.application.service;

import java.util.UUID;

/** An application as the evaluations module needs it (stage 9b), after the viewer's access was checked. */
public record EvaluationTarget(UUID applicationId, UUID jobId, UUID candidateId) {}
