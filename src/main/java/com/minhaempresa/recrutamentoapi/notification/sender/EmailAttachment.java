package com.minhaempresa.recrutamentoapi.notification.sender;

/**
 * A text attachment of an email (e.g. the {@code .ics} calendar invitation of an interview). Never logged.
 *
 * @param contentType full MIME type, e.g. {@code text/calendar; charset=UTF-8; method=REQUEST}
 */
public record EmailAttachment(String fileName, String contentType, String content) {

    @Override
    public String toString() {
        return "EmailAttachment(" + fileName + ")";
    }
}
