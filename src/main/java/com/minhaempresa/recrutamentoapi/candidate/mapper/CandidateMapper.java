package com.minhaempresa.recrutamentoapi.candidate.mapper;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSearchHitResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.CandidateSummaryResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ConsentResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.EducationResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.LanguageSkillResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.ReferenceResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.WorkExperienceResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Candidate;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateDocument;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateLanguage;
import com.minhaempresa.recrutamentoapi.candidate.entity.CandidateReference;
import com.minhaempresa.recrutamentoapi.candidate.entity.Consent;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.Education;
import com.minhaempresa.recrutamentoapi.candidate.entity.WorkExperience;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(imports = DocumentUrls.class)
public interface CandidateMapper {

    @Mapping(target = "isAngolan", source = "angolan")
    @Mapping(target = "sourceId", source = "source")
    @Mapping(target = "avatarUrl", expression = "java(DocumentUrls.inline(candidate.getPhotoDocumentId()))")
    CandidateResponse toResponse(Candidate candidate);

    WorkExperienceResponse toExperienceResponse(WorkExperience experience);

    EducationResponse toEducationResponse(Education education);

    LanguageSkillResponse toLanguageResponse(CandidateLanguage language);

    ConsentResponse toConsentResponse(Consent consent);

    @Mapping(target = "url", expression = "java(DocumentUrls.file(document.getId()))")
    DocumentResponse toDocumentResponse(CandidateDocument document);

    @Mapping(target = "checkedByName", source = "checkedByName")
    ReferenceResponse toReferenceResponse(CandidateReference reference, String checkedByName);

    default List<DriverLicenseCategory> toLicenses(List<String> values) {
        return values.stream().map(DriverLicenseCategory::valueOf).toList();
    }

    /** List view: no contacts nor personal data (minimization). Staff only, so the photo is included. */
    default CandidateSummaryResponse toSummary(Candidate candidate, int relevance, List<String> matchedTerms, long applications, long activeApplications) {
        return new CandidateSummaryResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getHeadline(),
                candidate.getProvince(),
                candidate.getCity(),
                DocumentUrls.inline(candidate.getPhotoDocumentId()),
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

    /** @param withPhoto {@code false} for interviewers (they cannot open the photo, decision D6-1) */
    default CandidateSearchHitResponse toSearchHit(Candidate candidate, boolean withPhoto) {
        return new CandidateSearchHitResponse(candidate.getId(), candidate.getFullName(), candidate.getHeadline(), candidate.getProvince(),
                withPhoto ? DocumentUrls.inline(candidate.getPhotoDocumentId()) : null);
    }
}
