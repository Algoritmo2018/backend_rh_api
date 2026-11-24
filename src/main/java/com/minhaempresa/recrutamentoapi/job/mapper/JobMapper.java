package com.minhaempresa.recrutamentoapi.job.mapper;

import com.minhaempresa.recrutamentoapi.job.dto.response.JobRequirementResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.PublicJobResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.SalaryRangeResponse;
import com.minhaempresa.recrutamentoapi.job.dto.response.ScreeningQuestionResponse;
import com.minhaempresa.recrutamentoapi.job.entity.Job;
import com.minhaempresa.recrutamentoapi.job.entity.JobRequirement;
import com.minhaempresa.recrutamentoapi.job.entity.SalaryRange;
import com.minhaempresa.recrutamentoapi.job.entity.ScreeningQuestion;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface JobMapper {

    @Mapping(target = "isConfidential", source = "confidential")
    @Mapping(target = "isFeatured", source = "featured")
    @Mapping(target = "isExpatriate", source = "expatriate")
    @Mapping(target = "testId", ignore = true)
    JobResponse toResponse(Job job);

    @Mapping(target = "jobId", source = "job.id")
    JobRequirementResponse toRequirementResponse(JobRequirement requirement);

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "order", expression = "java(question.getPosition() + 1)")
    ScreeningQuestionResponse toQuestionResponse(ScreeningQuestion question);

    default SalaryRangeResponse toSalaryResponse(SalaryRange salary) {
        if (salary == null) {
            return null;
        }
        return new SalaryRangeResponse(salary.min(), salary.max(), salary.currency(), salary.period(), Boolean.TRUE.equals(salary.visible()));
    }

    /**
     * Public view: {@code companyName} must already be {@code null} for confidential jobs; the salary is
     * hidden when not visible; no internal fields.
     */
    default PublicJobResponse toPublic(Job job, String companyName) {
        SalaryRangeResponse salary = toSalaryResponse(job.getSalary());
        return new PublicJobResponse(
                job.getId(),
                job.getSlug(),
                job.getTitle(),
                job.isConfidential() ? null : companyName,
                job.isConfidential(),
                job.getArea(),
                job.getProvince(),
                job.getCity(),
                job.getWorkMode(),
                job.getContractType(),
                job.getExperienceLevel(),
                job.getMinExperienceYears(),
                job.getVacancies(),
                job.getDescription(),
                List.copyOf(job.getResponsibilities()),
                List.copyOf(job.getBenefits()),
                salary != null && salary.visible() ? salary : null,
                job.getStatus(),
                job.isFeatured(),
                job.isExpatriate(),
                job.getDeadline(),
                job.getPublishedAt(),
                job.getRequirements().stream()
                        .map(r -> new PublicJobResponse.Requirement(r.getId(), r.getDescription(), r.getType(), r.getCategory(), r.getValue()))
                        .toList(),
                job.getScreeningQuestions().stream()
                        .map(q -> new PublicJobResponse.Question(q.getId(), q.getQuestion(), q.getType(), List.copyOf(q.getOptions()), q.isRequired(), q.getPosition() + 1))
                        .toList());
    }
}
