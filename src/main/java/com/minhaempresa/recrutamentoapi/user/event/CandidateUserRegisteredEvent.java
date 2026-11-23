package com.minhaempresa.recrutamentoapi.user.event;

import java.util.UUID;

/**
 * A candidate created an account in the portal. The candidates module (stage 6) listens to it to
 * create the candidate profile; linking to applications made before with the same email only happens
 * after the email is confirmed.
 */
public record CandidateUserRegisteredEvent(UUID userId, String email, String name, String phone) {}
