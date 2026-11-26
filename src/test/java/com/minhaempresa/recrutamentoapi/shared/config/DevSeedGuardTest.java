package com.minhaempresa.recrutamentoapi.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class DevSeedGuardTest {

    private static final List<String> MIGRATIONS = List.of("classpath:db/migration");
    private static final List<String> WITH_SEED = List.of("classpath:db/migration", "filesystem:dev/seed");

    private static MockEnvironment environment(String frontendUrl, String... profiles) {
        MockEnvironment environment = new MockEnvironment().withProperty("app.frontend-url", frontendUrl);
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8088/software_rh", "http://127.0.0.1:5173/software_rh", "http://LOCALHOST/software_rh"})
    void allowsTheSeedOnALocalMachine(String url) {
        assertThatCode(() -> DevSeedGuard.check(environment(url, "staging", "devseed"), WITH_SEED)).doesNotThrowAnyException();
    }

    @Test
    void refusesTheSeedForARemoteFrontend() {
        assertThatThrownBy(() -> DevSeedGuard.check(environment("https://recrutamento.example.ao/software_rh", "staging", "devseed"), WITH_SEED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only for a local machine")
                .hasMessageContaining("known")
                .hasMessageContaining("APP_DEVSEED_ALLOW_REMOTE=true");
    }

    @Test
    void refusesTheSeedLocationEvenWithoutTheProfile() {
        // e.g. SPRING_FLYWAY_LOCATIONS set by hand on a server
        assertThatThrownBy(() -> DevSeedGuard.check(environment("https://recrutamento.example.ao/software_rh", "staging"), WITH_SEED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void remoteFrontendNeedsTheExplicitSwitch() {
        MockEnvironment environment = environment("https://teste.example.ao/software_rh", "staging", "devseed")
                .withProperty(DevSeedGuard.ALLOW_REMOTE_PROPERTY, "true");

        assertThatCode(() -> DevSeedGuard.check(environment, WITH_SEED)).doesNotThrowAnyException();
    }

    @Test
    void neverWithTheProdProfileNotEvenWithTheSwitch() {
        MockEnvironment environment = environment("http://localhost:8088/software_rh", "prod", "devseed")
                .withProperty(DevSeedGuard.ALLOW_REMOTE_PROPERTY, "true");

        assertThatThrownBy(() -> DevSeedGuard.check(environment, WITH_SEED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'prod'");
    }

    @Test
    void doesNothingWithoutTheSeed() {
        assertThatCode(() -> DevSeedGuard.check(environment("https://recrutamento.example.ao/software_rh", "prod"), MIGRATIONS))
                .doesNotThrowAnyException();
    }
}
