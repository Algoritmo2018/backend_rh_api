package com.minhaempresa.recrutamentoapi.user.event;

/**
 * A staff or client user was invited (or re-invited): send the invitation email.
 *
 * @param token raw one-time token for the link (never logged)
 */
public record UserInvitedEvent(String email, String name, String token) {

    @Override
    public String toString() {
        return "UserInvitedEvent(email=" + email + ")";
    }
}
