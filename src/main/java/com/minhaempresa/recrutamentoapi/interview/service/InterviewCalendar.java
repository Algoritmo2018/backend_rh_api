package com.minhaempresa.recrutamentoapi.interview.service;

import com.minhaempresa.recrutamentoapi.interview.event.InterviewNotice;
import com.minhaempresa.recrutamentoapi.notification.sender.EmailAttachment;
import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Calendar invitation of an interview (RFC 5545 / 5546 {@code .ics}), attached to the emails (D9 §6):
 * {@code METHOD:REQUEST} when scheduled or changed, {@code METHOD:CANCEL} when cancelled; the {@code UID} is fixed per
 * interview and {@code SEQUENCE} grows on every change, so calendars update the same event. Times in UTC
 * ({@code …Z}). No personal data: the summary has only the job title (D9-5), never the candidate's name.
 */
@Component
@RequiredArgsConstructor
public class InterviewCalendar {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String CRLF = "\r\n";

    private final AppProperties appProperties;
    private final MailSenderProperties mailProperties;
    private final InterviewTexts texts;
    private final Messages messages;
    private final Clock clock;

    /** {@code null} for an interview without a time (waiting for a slot). */
    public EmailAttachment attachment(InterviewNotice interview, boolean cancel) {
        if (interview.startsAt() == null) {
            return null;
        }
        String method = cancel ? "CANCEL" : "REQUEST";
        return new EmailAttachment(cancel ? "cancelamento.ics" : "convite.ics", "text/calendar; charset=UTF-8; method=" + method, build(interview, cancel));
    }

    /** The calendar text (CRLF lines, folded at 75 octets). */
    String build(InterviewNotice interview, boolean cancel) {
        Instant start = interview.startsAt();
        String where = texts.where(interview.mode(), interview.location(), interview.meetingUrl());
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//FEH OIRAM//Recrutamento//PT");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:" + (cancel ? "CANCEL" : "REQUEST"));
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + uid(interview));
        lines.add("SEQUENCE:" + interview.icsSequence());
        lines.add("DTSTAMP:" + UTC.format(clock.instant()));
        lines.add("DTSTART:" + UTC.format(start));
        lines.add("DTEND:" + UTC.format(start.plusSeconds(interview.durationMinutes() * 60L)));
        lines.add("SUMMARY:" + escape(messages.get("interview.calendar.summary", interview.jobTitle())));
        if (where != null && !where.isBlank()) {
            lines.add("LOCATION:" + escape(where));
        }
        lines.add("DESCRIPTION:" + escape(messages.get("interview.calendar.description", interview.jobTitle(), texts.when(start), where == null ? "" : where)));
        lines.add("ORGANIZER;CN=FEH OIRAM:mailto:" + organizerAddress());
        lines.add("STATUS:" + (cancel ? "CANCELLED" : "CONFIRMED"));
        lines.add("TRANSP:OPAQUE");
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        StringBuilder out = new StringBuilder();
        lines.forEach(line -> out.append(fold(line)).append(CRLF));
        return out.toString();
    }

    /** Same for every change of the interview: {@code interview-<id>@<domain of the frontend>}. */
    String uid(InterviewNotice interview) {
        String host = URI.create(appProperties.frontendUrl()).getHost();
        return "interview-" + interview.interviewId() + "@" + (host == null ? "fehoiram.co.ao" : host);
    }

    /** The address of {@code app.mail.from} ({@code FEH OIRAM <nao-responder@…>} → the address). */
    private String organizerAddress() {
        String from = mailProperties.from();
        int open = from.indexOf('<');
        int close = from.indexOf('>');
        return open >= 0 && close > open ? from.substring(open + 1, close).trim() : from.trim();
    }

    /** TEXT values: backslash, semicolon, comma and line breaks escaped (RFC 5545 §3.3.11). */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "");
    }

    /** Lines longer than 75 octets continue on the next line after a space (never inside a UTF-8 character). */
    static String fold(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            return line;
        }
        StringBuilder out = new StringBuilder();
        int octets = 0;
        int limit = 75;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append(CRLF).append(' ');
                octets = 1;
                limit = 75;
            }
            out.append(character);
            octets += size;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }
}
