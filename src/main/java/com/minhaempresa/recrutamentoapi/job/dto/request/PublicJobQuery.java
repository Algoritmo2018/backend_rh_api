package com.minhaempresa.recrutamentoapi.job.dto.request;

import com.minhaempresa.recrutamentoapi.job.entity.ContractType;
import com.minhaempresa.recrutamentoapi.job.entity.ExperienceLevel;
import com.minhaempresa.recrutamentoapi.job.entity.WorkMode;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query of the public job list (frontend {@code PublicJobQuery}).
 *
 * @param q words searched in the title, city and description (accents ignored, word prefixes match)
 * @param postedWithinDays published in the last N days
 * @param pageSize 9 by default (like the frontend), at most 50
 */
public record PublicJobQuery(
        @Size(max = 100, message = "{job.search.size}") String q,
        Province province,
        JobArea area,
        ContractType contractType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        @Min(value = 1, message = "{job.posted-within.range}") @Max(value = 365, message = "{job.posted-within.range}") Integer postedWithinDays,
        PublicJobSort sort,
        @Min(value = 1, message = "{validation.page.min}") Integer page,
        @Min(value = 1, message = "{job.public-page-size.range}") @Max(value = 50, message = "{job.public-page-size.range}") Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 9;
}
