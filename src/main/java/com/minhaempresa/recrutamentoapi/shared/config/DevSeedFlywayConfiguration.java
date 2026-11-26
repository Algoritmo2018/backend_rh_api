package com.minhaempresa.recrutamentoapi.shared.config;

import java.util.Arrays;
import org.flywaydb.core.api.Location;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Runs {@link DevSeedGuard} before any migration, so a refused seed never touches the database. */
@Configuration(proxyBeanMethods = false)
class DevSeedFlywayConfiguration {

    @Bean
    FlywayMigrationStrategy devSeedGuardedMigration(Environment environment) {
        return flyway -> {
            DevSeedGuard.check(environment, Arrays.stream(flyway.getConfiguration().getLocations()).map(Location::toString).toList());
            flyway.migrate();
        };
    }
}
