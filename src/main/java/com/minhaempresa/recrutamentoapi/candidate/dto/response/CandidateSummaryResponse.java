package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.Availability;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Candidate in lists and in the talent search (frontend {@code TalentSearchResult}, reduced). Data
 * minimization: no contacts, birth date, gender, nationality, salary expectation nor documents; those
 * are only in the profile, for who may see them.
 *
 * @param relevance share of the search words found in the profile (0–100; 100 without {@code q})
 * @param matchedTerms search words found
 * @param applicationsCount applications of the candidate (0 until stage 8)
 * @param activeApplications applications still in progress (0 until stage 8)
 */
public record CandidateSummaryResponse(
        UUID id,
        String fullName,
        String headline,
        Province province,
        String city,
        String avatarUrl,
        List<String> skills,
        List<LanguageSkillResponse> languages,
        List<DriverLicenseCategory> driverLicenses,
        Availability availability,
        int totalExperienceYears,
        Set<UUID> tagIds,
        boolean inTalentPool,
        int relevance,
        List<String> matchedTerms,
        long applicationsCount,
        long activeApplications) {}
