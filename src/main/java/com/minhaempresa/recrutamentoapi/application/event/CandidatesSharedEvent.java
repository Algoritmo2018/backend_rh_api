package com.minhaempresa.recrutamentoapi.application.event;

import java.util.UUID;

/**
 * Applications of a job were shared with its client company (stage 8c): its portal users get a bell and an email,
 * purpose {@code clients}, without the candidates' names ("Novos candidatos apresentados para a vaga X").
 */
public record CandidatesSharedEvent(UUID jobId, String jobTitle, UUID clientId, int count) {}
