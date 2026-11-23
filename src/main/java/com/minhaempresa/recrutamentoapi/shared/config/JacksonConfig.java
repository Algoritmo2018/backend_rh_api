package com.minhaempresa.recrutamentoapi.shared.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3 ({@code tools.jackson}) customizations. Spring Boot registers every {@link JacksonModule}
 * bean in the application {@code JsonMapper}.
 */
@Configuration
public class JacksonConfig {

    /** ISO 8601 in UTC, always with milliseconds: {@code 2026-11-03T09:30:00.000Z}. */
    public static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * The frontend expects dates exactly as {@code Date.toISOString()} produces them. Jackson's default
     * drops the fraction when it is zero ({@code ...:00Z}), which breaks the string comparisons the
     * frontend does on ISO dates.
     */
    @Bean
    JacksonModule instantWithMillisModule() {
        return new SimpleModule("instant-with-millis").addSerializer(Instant.class, new InstantWithMillisSerializer());
    }

    static final class InstantWithMillisSerializer extends StdSerializer<Instant> {

        InstantWithMillisSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(INSTANT_FORMAT.format(value));
        }
    }
}
