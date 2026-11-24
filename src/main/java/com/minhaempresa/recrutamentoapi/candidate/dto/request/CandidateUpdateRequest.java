package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.Availability;
import com.minhaempresa.recrutamentoapi.candidate.entity.DriverLicenseCategory;
import com.minhaempresa.recrutamentoapi.candidate.entity.Gender;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Partial edit of the own profile ({@code PATCH /me/candidate}, frontend {@code Partial<Candidate>}):
 * {@code null} or absent = unchanged; a list, when sent, is the full list. Fields the candidate cannot
 * change ({@code email}, {@code cvDocumentId}, {@code inTalentPool}, {@code tagIds}, {@code sourceId},
 * {@code totalExperienceYears}…) are ignored.
 *
 * @param whatsapp {@code ""} removes it
 * @param birthDate age between 16 and 80
 * @param salaryExpectation {@code {amount: null}} removes it
 */
public record CandidateUpdateRequest(
        @PersonName String fullName,
        @AngolanPhone String phone,
        String whatsapp,
        Gender gender,
        LocalDate birthDate,
        @Size(min = 2, max = 40, message = "{candidate.nationality.size}") String nationality,
        Boolean isAngolan,
        Province province,
        @Size(min = 2, max = 60, message = "{candidate.city.size}") String city,
        @Size(min = 3, max = 80, message = "{candidate.headline.size}") String headline,
        @Size(max = 600, message = "{candidate.summary.size}") String summary,
        @Size(max = 30, message = "{candidate.experience.max}") List<@Valid @NotNull(message = "{validation.required}") WorkExperienceRequest> experience,
        @Size(max = 20, message = "{candidate.education.max}") List<@Valid @NotNull(message = "{validation.required}") EducationRequest> education,
        @Size(max = 30, message = "{candidate.skills.max}") List<@NotBlank(message = "{validation.required}") @Size(max = 60, message = "{candidate.skill.size}") String> skills,
        @Size(max = 10, message = "{candidate.languages.max}") List<@Valid @NotNull(message = "{validation.required}") LanguageSkillRequest> languages,
        @Size(max = 5, message = "{validation.invalid-value}") List<@NotNull(message = "{validation.required}") DriverLicenseCategory> driverLicenses,
        Availability availability,
        @Valid SalaryExpectationRequest salaryExpectation) {}
