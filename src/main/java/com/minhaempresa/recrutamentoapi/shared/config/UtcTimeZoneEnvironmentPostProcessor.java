package com.minhaempresa.recrutamentoapi.shared.config;

import java.time.ZoneOffset;
import java.util.TimeZone;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Sets the JVM default time zone to UTC before the application context starts, so every date
 * computed on the server (and stored by Hibernate, see {@code hibernate.jdbc.time_zone}) is in UTC.
 * Registered in {@code META-INF/spring.factories}.
 */
public class UtcTimeZoneEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }
}
