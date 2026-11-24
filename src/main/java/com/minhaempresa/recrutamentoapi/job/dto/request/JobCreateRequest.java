package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.ContractType;
import com.minhaempresa.recrutamentoapi.job.entity.ExperienceLevel;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import com.minhaempresa.recrutamentoapi.job.entity.WorkMode;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.validation.RichTextLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * New job (frontend {@code JobInput}, rules of {@code jobFormSchema}).
 *
 * @param jobRequestId client job request converted into this job (it must be pending; the job gets
 *     the request's client)
 * @param status {@code draft} or {@code open}
 * @param description limited HTML (p, strong, em, ul, li, br); anything else is removed before saving
 * @param testId technical test (stage 10); must be {@code null} until then
 * @param deadline {@code "YYYY-MM-DD"}, not in the past
 */
public record JobCreateRequest(
        @NotBlank(message = "{job.title.size}") @Size(min = 3, max = 90, message = "{job.title.size}") String title,
        @NotNull(message = "{job.client.required}") UUID clientId,
        boolean isConfidential,
        @NotNull(message = "{validation.required}") JobArea area,
        @NotNull(message = "{validation.required}") Province province,
        @NotBlank(message = "{job.city.size}") @Size(min = 2, max = 60, message = "{job.city.size}") String city,
        @NotNull(message = "{validation.required}") WorkMode workMode,
        @NotNull(message = "{validation.required}") ContractType contractType,
        @NotNull(message = "{validation.required}") ExperienceLevel experienceLevel,
        @NotNull(message = "{validation.required}") @Min(value = 0, message = "{job.min-experience.range}") @Max(value = 40, message = "{job.min-experience.range}") Integer minExperienceYears,
        @NotNull(message = "{validation.required}") @Min(value = 1, message = "{job.vacancies.min}") @Max(value = 500, message = "{job.vacancies.max}") Integer vacancies,
        boolean isExpatriate,
        boolean isFeatured,
        @NotNull(message = "{job.description.min}") @RichTextLength(minText = 40, maxHtml = 20_000, message = "{job.description.min}") String description,
        @Size(max = 20, message = "{job.list.max}") List<@NotBlank(message = "{validation.required}") @Size(max = 200, message = "{job.list-item.size}") String> responsibilities,
        @Size(max = 20, message = "{job.list.max}") List<@NotBlank(message = "{validation.required}") @Size(max = 200, message = "{job.list-item.size}") String> benefits,
        @Size(max = 30, message = "{job.requirements.max}") List<@Valid @NotNull(message = "{validation.required}") JobRequirementRequest> requirements,
        @Size(max = 20, message = "{job.questions.max}") List<@Valid @NotNull(message = "{validation.required}") ScreeningQuestionRequest> screeningQuestions,
        @Valid SalaryRangeRequest salary,
        @NotNull(message = "{validation.required}") JobStatus status,
        @NotNull(message = "{job.recruiter.required}") UUID recruiterId,
        @NotEmpty(message = "{job.interviewers.required}") @Size(max = 10, message = "{job.interviewers.max}") List<@NotNull(message = "{validation.required}") UUID> interviewerIds,
        UUID testId,
        LocalDate deadline,
        @Size(max = 20, message = "{job.tags.max}") List<@NotNull(message = "{validation.required}") UUID> tagIds,
        UUID jobRequestId) implements JobInput {}
