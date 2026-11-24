package com.minhaempresa.recrutamentoapi.job.dto.response;

import com.minhaempresa.recrutamentoapi.job.entity.ContractType;
import com.minhaempresa.recrutamentoapi.job.entity.ExperienceLevel;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementCategory;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementType;
import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestionType;
import com.minhaempresa.recrutamentoapi.job.entity.WorkMode;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Job as shown on the public portal (frontend {@code PublicJob}). Never contains internal data: no
 * client id, responsible staff, test, tags, job request or version; {@code companyName} is
 * {@code null} for confidential jobs and {@code salary} is {@code null} when not visible. Requirements
 * have no weight/minimum and questions no elimination rule (it would reveal the expected answer).
 */
public record PublicJobResponse(
        UUID id,
        String slug,
        String title,
        String companyName,
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
        LocalDate deadline,
        Instant publishedAt,
        List<Requirement> requirements,
        List<Question> screeningQuestions) {

    /** Public requirement: what the candidate needs to know, not how the score is computed. */
    public record Requirement(UUID id, String description, RequirementType type, RequirementCategory category, String value) {}

    /** Public screening question: no {@code eliminatory} nor {@code expectedAnswer}. */
    public record Question(UUID id, String question, ScreeningQuestionType type, List<String> options, boolean required, int order) {}
}
