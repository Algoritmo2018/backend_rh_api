package com.minhaempresa.recrutamentoapi.auth.event;

import java.time.Duration;

/** Send a 6-digit code by SMS. {@code code} is never logged. */
public record SmsCodeRequestedEvent(String phone, Purpose purpose, String code, Duration validity) {

    public enum Purpose {
        /** Phone login. */
        LOGIN,
        /** Enable two-step verification, or the second step of a password login. */
        TWO_FACTOR,
        /** Confirm a new phone number. */
        PHONE_CHANGE
    }

    @Override
    public String toString() {
        return "SmsCodeRequestedEvent(purpose=" + purpose + ")";
    }
}
