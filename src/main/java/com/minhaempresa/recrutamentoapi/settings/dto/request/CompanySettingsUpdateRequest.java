package com.minhaempresa.recrutamentoapi.settings.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update ({@code PATCH}): {@code null} fields are left unchanged. Same rules as the frontend
 * ({@code CompanyTabs}). The logo has its own endpoint.
 *
 * @param consentJobIncludesClientSharing mandatory when {@code consentJobText} changes: the administrator confirms
 *     whether the new text covers sharing with the client company (stage 8c); ignored otherwise (a version never
 *     changes)
 */
public record CompanySettingsUpdateRequest(
        @Size(min = 2, max = 80, message = "{settings.company.name-size}") String name,
        @Size(min = 3, max = 160, message = "{settings.company.legal-name-size}") String legalName,
        @Pattern(regexp = "\\d{9,14}", message = "{validation.nif.invalid}") String nif,
        @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @AngolanPhone String phone,
        @Size(min = 5, max = 200, message = "{settings.company.address-size}") String address,
        @Pattern(regexp = "https?://\\S+\\.\\S+", message = "{validation.url.invalid}") @Size(max = 200, message = "{validation.url.invalid}") String website,
        @Min(value = 6, message = "{settings.company.retention-range}") @Max(value = 60, message = "{settings.company.retention-range}") Integer dataRetentionMonths,
        @Size(min = 40, max = 1500, message = "{settings.company.consent-size}") String consentJobText,
        @Size(min = 40, max = 1500, message = "{settings.company.consent-size}") String consentTalentPoolText,
        Boolean consentJobIncludesClientSharing) {}
