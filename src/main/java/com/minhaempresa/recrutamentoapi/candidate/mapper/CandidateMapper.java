package com.minhaempresa.recrutamentoapi.candidate.mapper;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSearchHitResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSummaryResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ConsentResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.EducationResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.LanguageSkillResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.WorkExperienceResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateLanguage;
import com.minhaempresa.recrutamentoapi.candidate.entity.Consent;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.Education;
import com.minhaempresa.recrutamentoapi.candidate.entity.WorkExperience;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CandidateMapper {

    @Mapping(target = "isAngolan", source = "angolan")
    @Mapping(target = "sourceId", source = "source")
    @Mapping(target = "avatarUrl", ignore = true)
    CandidateResponse toResponse(Candidate candidate);

    WorkExperienceResponse toExperienceResponse(WorkExperience experience);

    EducationResponse toEducationResponse(Education education);

    LanguageSkillResponse toLanguageResponse(CandidateLanguage language);

    ConsentResponse toConsentResponse(Consent consent);

    default List<DriverLicenseCategory> toLicenses(List<String> values) {
        return values.stream().map(DriverLicenseCategory::valueOf).toList();
    }

    /** List view: no contacts nor personal data (minimization). */
    default CandidateSummaryResponse toSummary(Candidate candidate, int relevance, List<String> matchedTerms, long applications, long activeApplications) {
        return new CandidateSummaryResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getHeadline(),
                candidate.getProvince(),
                candidate.getCity(),
                null,
                List.copyOf(candidate.getSkills()),
                candidate.getLanguages().stream().map(this::toLanguageResponse).toList(),
                toLicenses(candidate.getDriverLicenses()),
                candidate.getAvailability(),
                candidate.getTotalExperienceYears(),
                Set.copyOf(candidate.getTagIds()),
                candidate.isInTalentPool(),
                relevance,
                List.copyOf(matchedTerms),
                applications,
                activeApplications);
    }

    default CandidateSearchHitResponse toSearchHit(Candidate candidate) {
        return new CandidateSearchHitResponse(candidate.getId(), candidate.getFullName(), candidate.getHeadline(), candidate.getProvince(), null);
    }
}
