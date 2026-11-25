package com.minhaempresa.recrutamentoapi.application.event;

/**
 * A public application for a job this email already applied to: nothing was stored and the email is told so
 * (purpose {@code recruitment}); the form answered as usual (stage 8, adjustment 2).
 */
public record DuplicateApplicationEvent(String email, String jobTitle) {

    @Override
    public String toString() {
        return "DuplicateApplicationEvent";
    }
}
