package com.minhaempresa.recrutamentoapi.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * The development seed ({@code dev/seed}, users with a known password) must never reach production: only
 * {@code application-dev.yml} may list it in the Flyway locations (integration plan I1, adjustment A1).
 */
class DevSeedLocationsTest {

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-prod.yml"})
    void baseAndProductionConfigurationNeverLoadTheDevSeed(String file) {
        Map<String, String> locations = flywayLocations(file);

        assertThat(locations.values())
                .as("spring.flyway.locations of %s", file)
                .noneMatch(value -> value.replace('\\', '/').contains("dev/seed"));
    }

    @Test
    void onlyTheDevProfileLoadsTheSeed() {
        assertThat(flywayLocations("application-dev.yml").values())
                .anyMatch(value -> value.contains("filesystem:dev/seed"));
    }

    /** Every {@code spring.flyway.locations} entry (comma-separated string or YAML list) of a config file. */
    private static Map<String, String> flywayLocations(String file) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(file));
        Properties properties = yaml.getObject();
        assertThat(properties).as(file).isNotNull();
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("spring.flyway.locations"))
                .collect(Collectors.toMap(key -> key, properties::getProperty));
    }
}
