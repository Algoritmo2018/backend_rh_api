package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.MatchResultResponse.RequirementMatch;
import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;
import com.minhaempresa.recrutamentoapi.job.dto.response.JobRequirementResponse;
import com.minhaempresa.recrutamentoapi.job.entity.RequirementType;
import com.minhaempresa.recrutamentoapi.shared.domain.WireEnums;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Match score (DECISOES.md #20, same rules as the frontend {@code lib/matching.ts}): each requirement
 * weighs {@code weight}, doubled when mandatory; the score is the share of the weight met (0–100,
 * rounded; 0 without requirements). Every requirement carries a Portuguese explanation.
 *
 * <p>Uses only {@link MatchProfile}: never age, gender, nationality, marital status nor photo. The only
 * personal criterion is the province of residence, when the job has a {@code location} requirement.
 */
@Component
@RequiredArgsConstructor
public class MatchScorer {

    private final Messages messages;

    public MatchResultResponse score(List<JobRequirementResponse> requirements, MatchProfile profile) {
        List<RequirementMatch> matches = new ArrayList<>();
        int total = 0;
        int earned = 0;
        for (JobRequirementResponse requirement : requirements) {
            RequirementMatch match = evaluate(requirement, profile);
            matches.add(match);
            int weight = requirement.weight() * (requirement.type() == RequirementType.MANDATORY ? 2 : 1);
            total += weight;
            if (match.met()) {
                earned += weight;
            }
        }
        int score = total == 0 ? 0 : (int) Math.round(earned * 100.0 / total);
        return new MatchResultResponse(score, matches);
    }

    private RequirementMatch evaluate(JobRequirementResponse requirement, MatchProfile profile) {
        return switch (requirement.category()) {
            case SKILL -> skill(requirement, profile);
            case EXPERIENCE -> experience(requirement, profile);
            case EDUCATION -> education(requirement, profile);
            case LANGUAGE -> language(requirement, profile);
            case LICENSE -> license(requirement, profile);
            case LOCATION -> location(requirement, profile);
        };
    }

    /** The skill equals, contains or is contained in the requirement value (accents ignored). */
    private RequirementMatch skill(JobRequirementResponse requirement, MatchProfile profile) {
        String target = Texts.fold(requirement.value() != null ? requirement.value() : requirement.description());
        Optional<String> found = profile.skills().stream()
                .filter(skill -> {
                    String folded = Texts.fold(skill);
                    return folded.equals(target) || folded.contains(target) || target.contains(folded);
                })
                .findFirst();
        return found.map(skill -> met(requirement, true, messages.get("match.skill.met", skill)))
                .orElseGet(() -> met(requirement, false, messages.get("match.skill.missing")));
    }

    private RequirementMatch experience(JobRequirementResponse requirement, MatchProfile profile) {
        int minimum = requirement.minValue() == null ? 0 : requirement.minValue();
        int years = profile.totalExperienceYears();
        return met(requirement, years >= minimum, messages.get("match.experience", years, minimum));
    }

    private RequirementMatch education(JobRequirementResponse requirement, MatchProfile profile) {
        EducationLevel minimum = parse(EducationLevel.class, requirement.value()).orElse(EducationLevel.SECONDARY);
        Optional<EducationLevel> best = profile.educationLevels().stream().max(Comparator.comparingInt(EducationLevel::rank));
        if (best.isEmpty()) {
            return met(requirement, false, messages.get("match.education.none"));
        }
        return met(requirement, best.get().rank() >= minimum.rank(),
                messages.get("match.education", educationLabel(best.get()), educationLabel(minimum)));
    }

    private RequirementMatch language(JobRequirementResponse requirement, MatchProfile profile) {
        String wanted = Texts.fold(requirement.value());
        int minimumRank = requirement.minValue() == null ? 2 : requirement.minValue();
        Optional<MatchProfile.Language> skill = profile.languages().stream().filter(l -> Texts.fold(l.name()).equals(wanted)).findFirst();
        if (skill.isEmpty()) {
            return met(requirement, false, messages.get("match.language.none", requirement.value()));
        }
        return met(requirement, skill.get().level().rank() >= minimumRank,
                messages.get("match.language", skill.get().name(), languageLabel(skill.get().level()), languageLabel(LanguageLevel.ofRank(minimumRank))));
    }

    private RequirementMatch license(JobRequirementResponse requirement, MatchProfile profile) {
        String category = requirement.value() == null ? "B" : requirement.value().trim();
        boolean met = profile.driverLicenses().contains(category);
        return met(requirement, met, messages.get(met ? "match.license.met" : "match.license.missing", category));
    }

    /** Province of residence equals the job location (the only personal criterion). */
    private RequirementMatch location(JobRequirementResponse requirement, MatchProfile profile) {
        if (profile.province() == null) {
            return met(requirement, false, messages.get("match.location.unknown", requirement.value()));
        }
        String province = profile.province().getValue();
        boolean met = Texts.fold(province).equals(Texts.fold(requirement.value()));
        return met(requirement, met, met
                ? messages.get("match.location.met", province)
                : messages.get("match.location.missing", province, requirement.value()));
    }

    private static RequirementMatch met(JobRequirementResponse requirement, boolean met, String detail) {
        return new RequirementMatch(requirement.id(), met, detail);
    }

    private String educationLabel(EducationLevel level) {
        return messages.get("education-level." + level.getValue());
    }

    private String languageLabel(LanguageLevel level) {
        return messages.get("language-level." + level.getValue());
    }

    private static <E extends Enum<E> & com.minhaempresa.recrutamentoapi.shared.domain.WireEnum> Optional<E> parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(WireEnums.fromValue(type, value.trim()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
