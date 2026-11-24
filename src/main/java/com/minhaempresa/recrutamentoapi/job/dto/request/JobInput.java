package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.ContractType;
import com.minhaempresa.recrutamentoapi.job.entity.ExperienceLevel;
import com.minhaempresa.recrutamentoapi.job.entity.JobStatus;
import com.minhaempresa.recrutamentoapi.job.entity.WorkMode;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Fields of the job form shared by create and update (frontend {@code JobInput}). */
public interface JobInput {

    String title();

    UUID clientId();

    boolean isConfidential();

    JobArea area();

    Province province();

    String city();

    WorkMode workMode();

    ContractType contractType();

    ExperienceLevel experienceLevel();

    Integer minExperienceYears();

    Integer vacancies();

    boolean isExpatriate();

    boolean isFeatured();

    String description();

    List<String> responsibilities();

    List<String> benefits();

    List<JobRequirementRequest> requirements();

    List<ScreeningQuestionRequest> screeningQuestions();

    SalaryRangeRequest salary();

    JobStatus status();

    UUID recruiterId();

    List<UUID> interviewerIds();

    UUID testId();

    LocalDate deadline();

    List<UUID> tagIds();
}
