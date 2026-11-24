package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import java.util.UUID;

/** Job in the global search of the top bar (frontend {@code SearchJobHit}). */
public record JobSearchHitResponse(UUID id, String title, String clientName, JobStatus status) {}
