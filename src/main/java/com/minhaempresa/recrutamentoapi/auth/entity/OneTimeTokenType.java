package com.minhaempresa.recrutamentoapi.auth.entity;

/** Purpose of a one-time token. Internal only (never sent to the frontend). */
public enum OneTimeTokenType {
    /** Link sent by email to choose a new password. */
    PASSWORD_RESET,
    /** Link sent by email to a new staff/client user to set the first password. */
    INVITATION,
    /** Link sent by email to a newly registered candidate. */
    EMAIL_VERIFICATION,
    /** 6-digit SMS code for the phone login. */
    LOGIN_OTP,
    /** 6-digit SMS code to enable two-step verification. */
    TWO_FACTOR_SETUP,
    /** Challenge token + 6-digit SMS code for the second step of a password login. */
    TWO_FACTOR_LOGIN,
    /** 6-digit SMS code sent to a new phone number; the hash binds the code to that number. */
    PHONE_CHANGE
}
