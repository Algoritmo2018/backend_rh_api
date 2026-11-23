package com.minhaempresa.recrutamentoapi.shared.config;

import com.minhaempresa.recrutamentoapi.RecrutamentoApiApplication;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers every {@code @ConfigurationProperties} record of the application (shared {@link AppProperties}
 * and the module-specific ones) and the {@link Clock} used for all business time computations.
 */
@Configuration
@ConfigurationPropertiesScan(basePackageClasses = RecrutamentoApiApplication.class)
public class AppConfig {

    /** Inject this instead of calling {@code Instant.now()}, so tests can move time forward. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
