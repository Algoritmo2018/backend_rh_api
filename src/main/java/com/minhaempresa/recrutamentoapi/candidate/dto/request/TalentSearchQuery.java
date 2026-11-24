package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.Availability;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Talent pool search (frontend {@code TalentSearchQuery}), as query parameters; lists are repeated
 * parameters ({@code ?provinces=Luanda&provinces=Benguela}).
 *
 * @param q free text, e.g. "operador de empilhadeira com carta em Luanda" (relevance ≥ 50% of the words)
 * @param skills every skill must be in the profile (part of the name, accents ignored)
 * @param languages {@code language:minLevel}, e.g. {@code Inglês:advanced}
 * @param talentPoolOnly ignored: only candidates with an active talent-pool consent are ever returned
 * @param pageSize 12 by default, at most 50
 */
public record TalentSearchQuery(
        @Size(max = 100, message = "{talent.search.size}") String q,
        @Size(max = 10, message = "{talent.filter.max}") List<@Size(max = 60, message = "{candidate.skill.size}") String> skills,
        List<Province> provinces,
        @Min(value = 0, message = "{talent.years.range}") @Max(value = 50, message = "{talent.years.range}") Integer minYears,
        @Min(value = 0, message = "{talent.years.range}") @Max(value = 50, message = "{talent.years.range}") Integer maxYears,
        EducationLevel minEducation,
        @Size(max = 5, message = "{talent.filter.max}")
        List<@Pattern(regexp = "^[^:]{2,30}:(basic|intermediate|advanced|fluent|native)$", message = "{talent.language.invalid}") String> languages,
        List<Availability> availability,
        @Size(max = 10, message = "{talent.filter.max}") List<UUID> tagIds,
        Boolean talentPoolOnly,
        @Min(value = 1, message = "{validation.page.min}") Integer page,
        @Min(value = 1, message = "{talent.page-size.range}") @Max(value = 50, message = "{talent.page-size.range}") Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 12;
}
