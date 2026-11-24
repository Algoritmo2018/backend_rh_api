package com.minhaempresa.recrutamentoapi.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobRequirementResponse;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementCategory;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementType;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/** Same cases as the frontend {@code lib/matching.ts}. */
class MatchScorerTest {

    static MatchScorer scorer;

    @BeforeAll
    static void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        LocaleContextHolder.setLocale(Locale.ROOT);
        scorer = new MatchScorer(new Messages(source));
    }

    static JobRequirementResponse requirement(RequirementType type, int weight, RequirementCategory category, String value, Integer minValue) {
        return new JobRequirementResponse(UUID.randomUUID(), UUID.randomUUID(), "Requisito", type, weight, category, value, minValue);
    }

    static MatchProfile profile() {
        return new MatchProfile(
                List.of("Microsoft Excel", "Primavera ERP"),
                5,
                List.of(EducationLevel.SECONDARY, EducationLevel.LICENTIATE),
                List.of(new MatchProfile.Language("Português", LanguageLevel.NATIVE), new MatchProfile.Language("Inglês", LanguageLevel.INTERMEDIATE)),
                List.of("B"),
                Province.LUANDA);
    }

    static String detail(JobRequirementResponse requirement) {
        return scorer.score(List.of(requirement), profile()).matches().getFirst().detail();
    }

    static boolean met(JobRequirementResponse requirement) {
        return scorer.score(List.of(requirement), profile()).matches().getFirst().met();
    }

    @Test
    void everyCategoryWithItsExplanation() {
        var skill = requirement(RequirementType.MANDATORY, 3, RequirementCategory.SKILL, "excel", null);
        assertThat(met(skill)).isTrue();
        assertThat(detail(skill)).isEqualTo("Tem a competência \"Microsoft Excel\"");
        assertThat(met(requirement(RequirementType.MANDATORY, 3, RequirementCategory.SKILL, "AutoCAD", null))).isFalse();

        var experience = requirement(RequirementType.MANDATORY, 3, RequirementCategory.EXPERIENCE, null, 3);
        assertThat(met(experience)).isTrue();
        assertThat(detail(experience)).isEqualTo("5 anos de experiência (mínimo 3)");
        assertThat(met(requirement(RequirementType.MANDATORY, 3, RequirementCategory.EXPERIENCE, null, 6))).isFalse();

        var education = requirement(RequirementType.MANDATORY, 3, RequirementCategory.EDUCATION, "bachelor", null);
        assertThat(met(education)).isTrue();
        assertThat(detail(education)).isEqualTo("Licenciatura (mínimo Bacharelato)");
        assertThat(met(requirement(RequirementType.MANDATORY, 3, RequirementCategory.EDUCATION, "master", null))).isFalse();

        var language = requirement(RequirementType.DESIRABLE, 2, RequirementCategory.LANGUAGE, "Ingles", 3);
        assertThat(met(language)).isFalse();
        assertThat(detail(language)).isEqualTo("Inglês: Intermédio (mínimo Avançado)");
        assertThat(detail(requirement(RequirementType.DESIRABLE, 2, RequirementCategory.LANGUAGE, "Francês", 2))).isEqualTo("Não indicou Francês");

        assertThat(detail(requirement(RequirementType.MANDATORY, 4, RequirementCategory.LICENSE, "B", null))).isEqualTo("Carta de condução categoria B");
        assertThat(detail(requirement(RequirementType.MANDATORY, 4, RequirementCategory.LICENSE, "C", null))).isEqualTo("Sem carta de condução categoria C");

        assertThat(detail(requirement(RequirementType.MANDATORY, 2, RequirementCategory.LOCATION, "Luanda", null))).isEqualTo("Reside em Luanda");
        assertThat(detail(requirement(RequirementType.MANDATORY, 2, RequirementCategory.LOCATION, "Benguela", null)))
                .isEqualTo("Reside em Luanda (vaga em Benguela)");
    }

    @Test
    void oneYearIsSingular() {
        MatchProfile junior = new MatchProfile(List.of(), 1, List.of(), List.of(), List.of(), null);
        var experience = requirement(RequirementType.MANDATORY, 1, RequirementCategory.EXPERIENCE, null, 2);
        assertThat(scorer.score(List.of(experience), junior).matches().getFirst().detail()).isEqualTo("1 ano de experiência (mínimo 2)");
        assertThat(scorer.score(List.of(requirement(RequirementType.MANDATORY, 1, RequirementCategory.EDUCATION, null, null)), junior)
                .matches().getFirst().detail()).isEqualTo("Formação não indicada");
    }

    @Test
    void mandatoryRequirementsCountDouble() {
        MatchResultResponse result = scorer.score(List.of(
                requirement(RequirementType.MANDATORY, 5, RequirementCategory.SKILL, "Primavera", null),
                requirement(RequirementType.DESIRABLE, 3, RequirementCategory.SKILL, "SAP", null)), profile());

        // 10 of 13
        assertThat(result.score()).isEqualTo(77);
        assertThat(result.matches()).extracting(MatchResultResponse.RequirementMatch::met).containsExactly(true, false);
        assertThat(scorer.score(List.of(), profile()).score()).isZero();
    }

    @Test
    void theProfileUsedByTheScoreHasNoProtectedData() {
        List<String> components = Arrays.stream(MatchProfile.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertThat(components).containsExactlyInAnyOrder("skills", "totalExperienceYears", "educationLevels", "languages", "driverLicenses", "province");
        assertThat(components).doesNotContain("birthDate", "gender", "nationality", "isAngolan", "maritalStatus", "avatarUrl");
    }
}
