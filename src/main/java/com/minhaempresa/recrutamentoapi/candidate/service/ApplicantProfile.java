package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.util.UUID;

/**
 * The profile of a logged-in candidate who applies: the application copies these values (D8-1).
 *
 * @param cvDocumentId the current CV of the profile, or {@code null}
 */
public record ApplicantProfile(UUID candidateId, String fullName, String email, String phone, Province province, UUID cvDocumentId) {

    @Override
    public String toString() {
        return "ApplicantProfile(candidateId=" + candidateId + ")";
    }
}
