package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.entity.EducationLevel;
import com.minhaempresa.recrutamentoapi.candidate.entity.LanguageLevel;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.util.List;

/**
 * The only profile data the match score may use (DECISOES.md #20). Age, gender, nationality, marital
 * status and photo are deliberately absent, so they can never influence the score.
 */
public record MatchProfile(
        List<String> skills,
        int totalExperienceYears,
        List<EducationLevel> educationLevels,
        List<Language> languages,
        List<String> driverLicenses,
        Province province) {

    public record Language(String name, LanguageLevel level) {}
}
