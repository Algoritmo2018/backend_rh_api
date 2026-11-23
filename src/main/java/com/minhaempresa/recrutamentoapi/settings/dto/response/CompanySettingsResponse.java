package com.minhaempresa.recrutamentoapi.settings.dto.response;

import java.time.Instant;

/**
 * Frontend {@code CompanySettings}. {@code logoUrl} is the versioned public URL of the logo
 * ({@code /api/v1/public/settings/logo?v=<hash>}) or {@code null} without logo.
 */
public record CompanySettingsResponse(
        String name,
        String legalName,
        String nif,
        String email,
        String phone,
        String address,
        String website,
        String logoUrl,
        int dataRetentionMonths,
        String consentTextVersion,
        String consentJobText,
        String consentTalentPoolText,
        Instant updatedAt) {}
