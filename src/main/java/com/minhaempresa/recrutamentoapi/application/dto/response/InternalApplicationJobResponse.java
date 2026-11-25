package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.job.dto.response.JobRequirementResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.ScreeningQuestionResponse;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code job} of the frontend {@code InternalApplication}.
 *
 * @param testId always {@code null} until stage 10
 */
public record InternalApplicationJobResponse(
        UUID id,
        String title,
        JobStatus status,
        boolean isConfidential,
        List<JobRequirementResponse> requirements,
        List<ScreeningQuestionResponse> screeningQuestions,
        UUID testId,
        UUID clientId,
        UUID recruiterId,
        Set<UUID> interviewerIds,
        String clientName,
        String recruiterName) {}
