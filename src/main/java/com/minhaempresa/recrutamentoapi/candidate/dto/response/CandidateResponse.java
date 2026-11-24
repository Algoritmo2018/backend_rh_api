package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.Availability;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.Gender;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Frontend {@code Candidate}: the full profile, for its owner and for administrators and recruiters.
 * Interviewers get {@link #withoutPrivateData()}.
 *
 * @param avatarUrl photo (stage 6b); {@code null} until then
 * @param sourceId where the candidate came from ({@code portal}, {@code whatsapp}…)
 */
public record CandidateResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String phone,
        String whatsapp,
        Gender gender,
        LocalDate birthDate,
        String nationality,
        boolean isAngolan,
        Province province,
        String city,
        String headline,
        String summary,
        String avatarUrl,
        List<WorkExperienceResponse> experience,
        List<EducationResponse> education,
        List<String> skills,
        List<LanguageSkillResponse> languages,
        List<DriverLicenseCategory> driverLicenses,
        Availability availability,
        Money salaryExpectation,
        UUID cvDocumentId,
        int totalExperienceYears,
        CandidateSource sourceId,
        Set<UUID> tagIds,
        boolean inTalentPool,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Interviewer view (decision D6-1): no contacts, birth date, salary expectation nor photo (a
     * document they cannot open). The CV stays available to interviewers on purpose, although it may
     * contain contacts.
     */
    public CandidateResponse withoutPrivateData() {
        return new CandidateResponse(id, null, fullName, null, null, null, gender, null, nationality, isAngolan, province, city,
                headline, summary, null, experience, education, skills, languages, driverLicenses, availability, null,
                cvDocumentId, totalExperienceYears, sourceId, tagIds, inTalentPool, createdAt, updatedAt);
    }
}
