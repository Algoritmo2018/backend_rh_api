package com.minhaempresa.recrutamentoapi.candidate.service;

import java.util.UUID;

/**
 * Where to reach a candidate (messages module). Internal to the backend: never returned by a controller.
 *
 * @param whatsapp E.164 number, {@code null} when the candidate has none
 */
public record CandidateContact(UUID id, String fullName, String email, String phone, String whatsapp) {

    public boolean hasWhatsapp() {
        return whatsapp != null && !whatsapp.isBlank();
    }

    /** First word of the name ({@code {{nome}}}). */
    public String firstName() {
        return fullName == null || fullName.isBlank() ? "" : fullName.trim().split("\\s+")[0];
    }

    @Override
    public String toString() {
        return "CandidateContact(id=" + id + ")";
    }
}
