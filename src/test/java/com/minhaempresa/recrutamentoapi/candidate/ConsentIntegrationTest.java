package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class ConsentIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    CandidateApi candidates;

    @BeforeEach
    void setUp() {
        candidates = new CandidateApi(mvc, users, userService);
    }

    @Test
    void talentPoolConsentIsRecordedWithTheTextVersionAndNeverOverwritten() throws Exception {
        TestUser user = candidates.verifiedCandidate();
        String version = jdbc.queryForObject("SELECT consent_text_version FROM company_settings", String.class);

        JsonNode granted = candidates.setConsent(user, "talent_pool", true);
        assertThat(granted).hasSize(1);
        assertThat(granted.get(0).path("type").asString()).isEqualTo("talent_pool");
        assertThat(granted.get(0).path("granted").asBoolean()).isTrue();
        assertThat(granted.get(0).path("textVersion").asString()).isEqualTo(version);
        assertThat(granted.get(0).path("revokedAt").isNull()).isTrue();
        assertThat(profile(user).path("inTalentPool").asBoolean()).isTrue();

        // Granting again does not duplicate
        assertThat(candidates.setConsent(user, "talent_pool", true)).hasSize(1);

        JsonNode revoked = candidates.setConsent(user, "talent_pool", false);
        assertThat(revoked).hasSize(1);
        assertThat(revoked.get(0).path("revokedAt").isNull()).isFalse();
        assertThat(profile(user).path("inTalentPool").asBoolean()).isFalse();

        // A new grant is a new row: the history keeps the revoked one
        JsonNode again = candidates.setConsent(user, "talent_pool", true);
        assertThat(again).hasSize(2);
        assertThat(again.get(0).path("revokedAt").isNull()).isTrue();
        assertThat(again.get(1).path("revokedAt").isNull()).isFalse();

        JsonNode marketing = candidates.setConsent(user, "marketing", true);
        assertThat(marketing).hasSize(3);
        assertThat(profile(user).path("inTalentPool").asBoolean()).isTrue();
        assertThat(json(mvc.perform(get("/api/v1/me/consents").with(users.bearer(user))))).hasSize(3);
    }

    @Test
    void theJobApplicationConsentIsOnlyGivenWhenApplying() throws Exception {
        TestUser user = candidates.verifiedCandidate();
        candidates.api().sendJson("PUT", "/api/v1/me/consents/job_application", users.accessToken(user), Map.of("granted", true))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Este consentimento é dado na candidatura a uma vaga."));
        candidates.api().sendJson("PUT", "/api/v1/me/consents/newsletter", users.accessToken(user), Map.of("granted", true))
                .andExpect(status().isBadRequest());
    }

    JsonNode profile(TestUser user) throws Exception {
        return json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(user))).andExpect(status().isOk()));
    }
}
