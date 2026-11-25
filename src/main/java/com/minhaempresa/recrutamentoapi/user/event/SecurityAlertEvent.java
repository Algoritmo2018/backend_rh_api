package com.minhaempresa.recrutamentoapi.user.event;

import java.time.Instant;

/**
 * A security-relevant change on an account: the owner is told by email what changed and when, and how
 * to react if it was not them.
 */
public record SecurityAlertEvent(String email, String name, Kind kind, Instant occurredAt) {

    public enum Kind {
        PASSWORD_CHANGED,
        PASSWORD_RESET,
        PHONE_CHANGED,
        TWO_FACTOR_ENABLED,
        TWO_FACTOR_DISABLED,
        /** Sent to the OLD address once the new one is confirmed. */
        EMAIL_CHANGED
    }
}
