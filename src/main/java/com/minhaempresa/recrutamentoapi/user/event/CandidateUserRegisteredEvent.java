package com.minhaempresa.recrutamentoapi.user.event;

import java.util.UUID;

/**
 * A candidate created an account in the portal. The candidate profile is NOT created here: it is created
 * or linked (applications made before with the same email) only after the email is confirmed
 * ({@link CandidateEmailVerifiedEvent}, decision D6-2).
 */
public record CandidateUserRegisteredEvent(UUID userId, String email, String name, String phone) {}
