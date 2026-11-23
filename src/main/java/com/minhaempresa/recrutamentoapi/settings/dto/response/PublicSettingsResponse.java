package com.minhaempresa.recrutamentoapi.settings.dto.response;

/**
 * What the public portal needs (application wizard, privacy page): contacts, logo, retention and the
 * current consent texts. No NIF, no audit data.
 */
public record PublicSettingsResponse(
        String name,
        String legalName,
        String email,
        String phone,
        String address,
        String website,
        String logoUrl,
        int dataRetentionMonths,
        String consentTextVersion,
        String consentJobText,
        String consentTalentPoolText) {}
