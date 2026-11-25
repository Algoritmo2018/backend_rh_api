package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.dto.response.EducationResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.LanguageSkillResponse;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.WorkExperienceResponse;
import com.minhaempresa.recrutamentoapi.candidate.entity.Availability;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import java.util.List;

/**
 * What a client company may see of a stored profile when an application is shared with it (stage 8c, approved
 * field list). Never contacts, birth date, gender, nationality, salary expectation, photo, city nor documents.
 */
public record SharedProfile(
        String headline,
        String summary,
        List<WorkExperienceResponse> experience,
        List<EducationResponse> education,
        List<LanguageSkillResponse> languages,
        List<String> skills,
        List<DriverLicenseCategory> driverLicenses,
        Availability availability,
        int totalExperienceYears) {}
