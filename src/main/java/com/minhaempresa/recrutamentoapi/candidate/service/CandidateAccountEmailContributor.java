package com.minhaempresa.recrutamentoapi.candidate.service;

import com.minhaempresa.recrutamentoapi.candidate.repository.CandidateRepository;
import com.minhaempresa.recrutamentoapi.user.service.AccountEmailContributor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code candidates.email} follows the confirmed email change of the linked account (stage 7b). A candidate
 * without an account (made by applications) owns its email too: an account can never take it over, so the
 * profiles are never merged. Applications keep the email they were sent with (stage 8).
 */
@Component
@RequiredArgsConstructor
class CandidateAccountEmailContributor implements AccountEmailContributor {

    private final CandidateRepository repository;

    @Override
    public boolean isEmailUsedByOthers(String email, UUID userId) {
        return repository.findByEmail(email).filter(candidate -> !userId.equals(candidate.getUserId())).isPresent();
    }

    @Override
    public void onAccountEmailChanged(UUID userId, String newEmail) {
        repository.findByUserId(userId).ifPresent(candidate -> candidate.changeEmail(newEmail));
    }
}
