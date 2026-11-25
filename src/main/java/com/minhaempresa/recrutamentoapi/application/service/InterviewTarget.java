package com.minhaempresa.recrutamentoapi.application.service;

import com.minhaempresa.recrutamentoapi.application.entity.ApplicationStatus;
import java.util.UUID;

/**
 * An application as the interviews module needs it (stage 9).
 *
 * @param visibleToClient shared with the client and still in the process (a client interview needs it)
 */
public record InterviewTarget(UUID applicationId, UUID jobId, UUID candidateId, ApplicationStatus status, boolean visibleToClient) {}
