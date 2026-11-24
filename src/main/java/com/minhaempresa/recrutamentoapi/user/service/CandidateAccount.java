package com.minhaempresa.recrutamentoapi.user.service;

import java.util.UUID;

/**
 * Account of a candidate as the candidates module needs it (never returned by a controller).
 *
 * @param candidateId linked profile ({@code null} before the email is confirmed)
 */
public record CandidateAccount(UUID userId, String name, String email, String phone, boolean emailVerified, UUID candidateId) {

    @Override
    public String toString() {
        return "CandidateAccount(userId=" + userId + ")";
    }
}
