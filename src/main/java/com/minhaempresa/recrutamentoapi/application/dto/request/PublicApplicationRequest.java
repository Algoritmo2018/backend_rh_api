package com.minhaempresa.recrutamentoapi.application.dto.request;

import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Part {@code application} (JSON) of {@code POST /public/applications}; the CV is the optional file part
 * {@code cv}. Either a CV or the quick profile is required.
 *
 * @param source where the applicant came from ({@code ?origem=}); default {@code portal}
 * @param website honeypot: left empty by people (filled → same answer, nothing stored)
 */
public record PublicApplicationRequest(
        @NotNull(message = "{validation.required}") UUID jobId,
        @NotBlank(message = "{validation.required}") @PersonName String fullName,
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @NotNull(message = "{validation.required}") Province province,
        @Valid QuickProfileRequest quickProfile,
        @Size(max = 50, message = "{validation.invalid-value}") List<@Valid @NotNull(message = "{validation.required}") ScreeningAnswerRequest> screeningAnswers,
        @NotNull(message = "{validation.consent-job.required}") @AssertTrue(message = "{validation.consent-job.required}") Boolean consentJob,
        boolean consentTalentPool,
        CandidateSource source,
        String website) {

    @Override
    public String toString() {
        return "PublicApplicationRequest(jobId=" + jobId + ")";
    }
}
