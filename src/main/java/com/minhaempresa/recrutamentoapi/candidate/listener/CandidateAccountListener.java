package com.minhaempresa.recrutamentoapi.candidate.listener;

import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.user.event.CandidateEmailVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates or links the candidate profile when a candidate account confirms its email (decision D6-2).
 * Synchronous, in the transaction of the confirmation: the profile exists as soon as the confirmation
 * succeeds, and both are undone together.
 */
@Component
@RequiredArgsConstructor
public class CandidateAccountListener {

    private final CandidateService candidateService;

    @EventListener
    public void onEmailVerified(CandidateEmailVerifiedEvent event) {
        candidateService.linkOrCreateForAccount(event.userId(), event.email(), event.name(), event.phone());
    }
}
