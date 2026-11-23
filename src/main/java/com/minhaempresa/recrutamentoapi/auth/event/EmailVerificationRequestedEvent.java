package com.minhaempresa.recrutamentoapi.auth.event;

/** Send the email-confirmation link. {@code token} is the raw one-time token (never logged). */
public record EmailVerificationRequestedEvent(String email, String name, String token) {

    @Override
    public String toString() {
        return "EmailVerificationRequestedEvent(email=" + email + ")";
    }
}
