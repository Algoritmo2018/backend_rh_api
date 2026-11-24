package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.minhaempresa.recrutamentoapi.job.entity.ContractType;
import com.minhaempresa.recrutamentoapi.job.entity.ExperienceLevel;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import com.minhaempresa.recrutamentoapi.job.entity.WorkMode;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Frontend {@code Job} (internal view) plus {@code jobRequestId} and {@code version} (send it back on
 * {@code PUT}).
 *
 * @param testId always {@code null} until the tests module (stage 10)
 * @param deadline {@code "YYYY-MM-DD"}
 */
public record JobResponse(
        UUID id,
        String slug,
        String title,
        UUID clientId,
        boolean isConfidential,
        JobArea area,
        Province province,
        String city,
        WorkMode workMode,
        ContractType contractType,
        ExperienceLevel experienceLevel,
        int minExperienceYears,
        int vacancies,
        String description,
        List<String> responsibilities,
        List<String> benefits,
        SalaryRangeResponse salary,
        JobStatus status,
        boolean isFeatured,
        boolean isExpatriate,
        UUID recruiterId,
        Set<UUID> interviewerIds,
        UUID testId,
        LocalDate deadline,
        Instant publishedAt,
        Instant closedAt,
        List<JobRequirementResponse> requirements,
        List<ScreeningQuestionResponse> screeningQuestions,
        Set<UUID> tagIds,
        Instant createdAt,
        Instant updatedAt,
        UUID jobRequestId,
        long version) {}
