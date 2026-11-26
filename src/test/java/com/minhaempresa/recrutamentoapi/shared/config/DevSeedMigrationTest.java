package com.minhaempresa.recrutamentoapi.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Applies every migration plus the development seed ({@code dev/seed}, the same location as the dev profile)
 * on a fresh database and logs in with seeded users, so a schema change that breaks the seed fails here
 * instead of on a developer machine. Own application context (own containers): the seed never reaches the
 * database of the other integration tests.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/test-migration,filesystem:dev/seed")
class DevSeedMigrationTest {

    /** Development password of every seeded account (also in dev/README.md). */
    private static final String DEV_PASSWORD = "FehDev2026";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository userRepository;

    AuthApi api;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
    }

    @Test
    void seedIsAppliedAndEverySeededUserLoadsAsAnEntity() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script = 'R__dev_seed.sql' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);

        // JPA mapping of every seeded row (enum values, nullable columns)
        assertThat(userRepository.findAll()).filteredOn(u -> u.getEmail().endsWith(".local")).hasSize(14);
    }

    @Test
    void seededStaffClientAndCandidateLogIn() throws Exception {
        Session admin = api.login("ana.costa@fehoiram.local", DEV_PASSWORD);
        assertThat(admin.status()).isEqualTo(200);
        assertThat(admin.body().path("user").path("role").asString()).isEqualTo("admin");

        Session client = api.login("helena.bento@petrokwanza.local", DEV_PASSWORD);
        assertThat(client.status()).isEqualTo(200);
        assertThat(client.claims().path("clientId").asString()).isEqualTo("d0000000-0000-4000-8000-000000000201");

        Session candidate = api.login("luisa.fernandes@exemplo.local", DEV_PASSWORD);
        assertThat(candidate.status()).isEqualTo(200);
        assertThat(candidate.body().path("user").path("candidateId").asString()).isEqualTo("d0000000-0000-4000-8000-000000000401");
        assertThat(candidate.body().path("user").path("emailVerified").asBoolean()).isTrue();

        Session unconfirmed = api.login("pedro.vaz@exemplo.local", DEV_PASSWORD);
        assertThat(unconfirmed.status()).isEqualTo(200);
        assertThat(unconfirmed.body().path("user").path("emailVerified").asBoolean()).isFalse();
    }

    @Test
    void seededSpecialCasesBehaveAsDocumented() throws Exception {
        Session twoFactor = api.login("carlos.neto@fehoiram.local", DEV_PASSWORD);
        assertThat(twoFactor.status()).isEqualTo(200);
        assertThat(twoFactor.body().path("twoFactorRequired").asBoolean()).isTrue();
        assertThat(twoFactor.refreshToken()).isNull();

        assertThat(api.login("nuno.lopes@fehoiram.local", DEV_PASSWORD).status()).as("deactivated user").isEqualTo(401);
        assertThat(api.login("teresa.lima@mbanza.local", DEV_PASSWORD).status()).as("inactive client company").isEqualTo(401);
        assertThat(api.login("sara.mendes@fehoiram.local", DEV_PASSWORD).status()).as("invitation not accepted").isEqualTo(401);

        Session admin = api.login("ana.costa@fehoiram.local", DEV_PASSWORD);
        String body = api.sendJson("GET", "/api/v1/users", admin.accessToken(), null)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode users = JsonMapper.builder().build().readTree(body);
        boolean saraPending = false;
        for (JsonNode user : users) {
            if ("sara.mendes@fehoiram.local".equals(user.path("email").asString())) {
                saraPending = user.path("invitationPending").asBoolean();
            }
        }
        assertThat(saraPending).as("Sara Mendes has a pending invitation").isTrue();
    }
}
