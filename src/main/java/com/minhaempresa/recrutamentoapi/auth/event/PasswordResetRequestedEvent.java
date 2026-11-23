package com.minhaempresa.recrutamentoapi.auth.event;

/** Send the password-reset link. {@code token} is the raw one-time token (never logged). */
public record PasswordResetRequestedEvent(String email, String name, String token) {

    @Override
    public String toString() {
        return "PasswordResetRequestedEvent(email=" + email + ")";
    }
}
