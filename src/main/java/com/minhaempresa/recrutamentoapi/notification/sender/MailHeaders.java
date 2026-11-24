package com.minhaempresa.recrutamentoapi.notification.sender;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Header values of an email built from data that may be untrusted (candidate names in subjects, sender
 * names typed by administrators). CR and LF are removed so a value can never start a new header
 * ({@code "Ana\r\nBcc: x@evil.com"} stays one line of the subject).
 */
public final class MailHeaders {

    private MailHeaders() {}

    /** Line breaks (and other control characters) replaced by a space; trimmed. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t\\u0000-\\u001F\\u007F\\u2028\\u2029]+", " ").trim();
    }

    /**
     * A strict address ({@code user@domain}, no list, no group), with an optional display name.
     *
     * @throws IllegalArgumentException if the address is not a single valid mailbox
     */
    public static InternetAddress address(String email, String displayName) {
        String cleanEmail = clean(email);
        String cleanName = clean(displayName);
        try {
            InternetAddress address = new InternetAddress(cleanEmail, true);
            address.validate();
            if (address.isGroup() || cleanEmail.contains(",")) {
                throw new IllegalArgumentException("Not a single mailbox");
            }
            if (cleanName != null && !cleanName.isEmpty()) {
                address.setPersonal(cleanName, StandardCharsets.UTF_8.name());
            }
            return address;
        } catch (AddressException | UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Invalid email address", e);
        }
    }

    /** {@code "FEH OIRAM <nao-responder@x.ao>"} → that mailbox (the configured default sender). */
    public static InternetAddress parse(String mailbox) {
        try {
            InternetAddress address = new InternetAddress(clean(mailbox), true);
            return address(address.getAddress(), address.getPersonal());
        } catch (AddressException e) {
            throw new IllegalArgumentException("Invalid email address", e);
        }
    }
}
