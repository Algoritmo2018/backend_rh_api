package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.job.dto.response.JobRequirementResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.ScreeningQuestionResponse;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A job as the applications module needs it (internal: requirements with weights and questions with the
 * elimination rule are never shown to a candidate).
 *
 * @param publiclyVisible {@code open} and the deadline (if any) not passed (Angola time): accepts applications
 * @param clientName the client company name (also for confidential jobs: never show it to candidates then)
 */
public record ApplicableJob(
        UUID id,
        String title,
        JobStatus status,
        boolean publiclyVisible,
        boolean confidential,
        UUID clientId,
        String clientName,
        UUID recruiterId,
        Set<UUID> interviewerIds,
        List<JobRequirementResponse> requirements,
        List<ScreeningQuestionResponse> questions) {}
