package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import jakarta.validation.constraints.NotNull;

/** Publish, pause or close a job (menu of the job list). */
public record JobStatusRequest(@NotNull(message = "{validation.required}") JobStatus status) {}
