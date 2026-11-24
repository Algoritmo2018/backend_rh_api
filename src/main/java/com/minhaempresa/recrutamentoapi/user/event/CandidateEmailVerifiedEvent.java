package com.minhaempresa.recrutamentoapi.user.event;

import java.util.UUID;

/**
 * A candidate account proved it owns its email (confirmation link or password reset). Published in the
 * same transaction: the candidates module creates the profile, or links the candidate that already
 * has this email (applications made without an account), only now (decision D6-2).
 */
public record CandidateEmailVerifiedEvent(UUID userId, String email, String name, String phone) {}
