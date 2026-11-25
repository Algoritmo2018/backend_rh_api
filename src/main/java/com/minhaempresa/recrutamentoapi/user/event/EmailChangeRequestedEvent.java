package com.minhaempresa.recrutamentoapi.user.event;

/**
 * Send the confirmation link of a candidate's email change to the NEW address. {@code token} is the raw
 * one-time token (never logged, never stored in the outbox).
 */
public record EmailChangeRequestedEvent(String newEmail, String name, String token) {

    @Override
    public String toString() {
        return "EmailChangeRequestedEvent(newEmail=" + newEmail + ")";
    }
}
