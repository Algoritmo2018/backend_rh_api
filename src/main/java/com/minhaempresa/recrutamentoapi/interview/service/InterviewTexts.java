package com.minhaempresa.recrutamentoapi.interview.service;

import com.minhaempresa.recrutamentoapi.interview.entity.InterviewMode;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Interview times and places as people read them: always in Angola time ({@code app.defaults.time-zone}), in
 * Portuguese, e.g. "terça-feira, 2 de dezembro de 2026, 10:00 (hora de Luanda)". Stored times stay UTC.
 */
@Component
@RequiredArgsConstructor
public class InterviewTexts {

    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", PORTUGUESE);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PORTUGUESE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", PORTUGUESE);

    private final AppProperties appProperties;
    private final Messages messages;

    /** {@code {{data_entrevista}}}: the long local time, or "data a definir" while waiting for a slot. */
    public String when(Instant startsAt) {
        return startsAt == null ? messages.get("interview.time.pending") : messages.get("interview.time.text", LONG.format(local(startsAt)));
    }

    /** "02/12/2026 10:00–10:30 (hora de Luanda)" (conflict messages). */
    public String range(Instant startsAt, int durationMinutes) {
        ZonedDateTime start = local(startsAt);
        return messages.get("interview.time.range", SHORT_DATE.format(start), TIME.format(start.plusMinutes(durationMinutes)));
    }

    /** {@code {{local_entrevista}}}: the address, the meeting link or "Por telefone". */
    public String where(InterviewMode mode, String location, String meetingUrl) {
        return switch (mode) {
            case ONSITE -> location;
            case VIDEO -> meetingUrl;
            case PHONE -> messages.get("interview.location.phone");
        };
    }

    private ZonedDateTime local(Instant instant) {
        return instant.atZone(appProperties.defaults().timeZone());
    }
}
