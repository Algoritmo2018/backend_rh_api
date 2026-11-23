package com.minhaempresa.recrutamentoapi.notification.email;

/**
 * Content of an email with one call-to-action link (invitation, password reset, email confirmation,
 * security alert). Every text is already resolved from {@code messages.properties}.
 *
 * @param note small print under the button (link validity, or what to do if it was not the user)
 */
public record ActionEmail(
        String subject,
        String greeting,
        String body,
        String actionLabel,
        String actionUrl,
        String note) {}
