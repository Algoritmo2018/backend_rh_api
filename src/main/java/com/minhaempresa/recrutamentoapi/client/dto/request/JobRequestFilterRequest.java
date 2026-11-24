package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequestStatus;
import java.util.UUID;

/** Query parameters of {@code GET /job-requests} (frontend {@code getAll({clientId, status})}). */
public record JobRequestFilterRequest(UUID clientId, JobRequestStatus status) {}
