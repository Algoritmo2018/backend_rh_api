package com.minhaempresa.recrutamentoapi.application.dto.request;

import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Part {@code application} (JSON) of {@code POST /me/applications}: a logged-in candidate applies with the
 * own profile (name, email, phone and province come from it, never from the request). The optional file part
 * {@code cv} is a CV for this application only; without it the profile's current CV is used.
 */
public record MyApplicationRequest(
        @NotNull(message = "{validation.required}") UUID jobId,
        @Valid QuickProfileRequest quickProfile,
        @Size(max = 50, message = "{validation.invalid-value}") List<@Valid @NotNull(message = "{validation.required}") ScreeningAnswerRequest> screeningAnswers,
        @NotNull(message = "{validation.consent-job.required}") @AssertTrue(message = "{validation.consent-job.required}") Boolean consentJob,
        boolean consentTalentPool,
        CandidateSource source) {}
