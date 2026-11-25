package com.minhaempresa.recrutamentoapi.application.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.EducationResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.LanguageSkillResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.WorkExperienceResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Availability;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.util.List;

/**
 * The candidate as the client company sees it (stage 8c, approved field list). Never: phone, email, WhatsApp, birth
 * date, gender, nationality, salary expectation, documents. {@code city} and {@code avatarUrl} are always
 * {@code null} (kept so the frontend {@code Candidate} shape stays). An anonymous application shows only what it
 * sent (name, province, quick profile): never the stored profile.
 *
 * @param fullName and {@code province}: the copy sent with the application (D8-1)
 */
public record ClientSharedCandidateResponse(
        String fullName,
        String headline,
        String summary,
        Province province,
        String city,
        String avatarUrl,
        List<WorkExperienceResponse> experience,
        List<EducationResponse> education,
        List<LanguageSkillResponse> languages,
        List<String> skills,
        List<DriverLicenseCategory> driverLicenses,
        Availability availability,
        int totalExperienceYears) {}
