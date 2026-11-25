package com.minhaempresa.recrutamentoapi.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.StageRequests;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CatalogIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    TestUser admin;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        admin = users.create(UserRole.ADMIN);
    }

    private JsonNode getJson(String url, TestUser user) throws Exception {
        return JSON.readTree(mvc.perform(get(url).with(users.bearer(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private int statusOf(String method, String url, TestUser user, Object body) throws Exception {
        if (method.equals("GET")) {
            return mvc.perform(get(url).with(users.bearer(user))).andReturn().getResponse().getStatus();
        }
        return api.sendJson(method, url, users.accessToken(user), body).andReturn().getResponse().getStatus();
    }

    @Nested
    class Permissions {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "RECRUITER,   200, 200, 403, 403, 200, 403",
            "INTERVIEWER, 200, 200, 403, 403, 403, 403",
            "CLIENT,      403, 403, 403, 403, 403, 403",
            "CANDIDATE,   403, 403, 403, 403, 403, 403",
        })
        void readsAndWritesPerRole(UserRole role, int readStages, int readReasons, int writeTag, int readCompany, int readOfferTemplates, int includeInactive) throws Exception {
            TestUser user = users.create(role);

            assertThat(statusOf("GET", "/api/v1/settings/stages", user, null)).isEqualTo(readStages);
            assertThat(statusOf("GET", "/api/v1/settings/tags", user, null)).isEqualTo(readStages);
            assertThat(statusOf("GET", "/api/v1/message-templates", user, null)).isEqualTo(readStages);
            assertThat(statusOf("GET", "/api/v1/settings/rejection-reasons", user, null)).isEqualTo(readReasons);
            assertThat(statusOf("GET", "/api/v1/settings/rejection-reasons?includeInactive=true", user, null)).isEqualTo(includeInactive);
            assertThat(statusOf("POST", "/api/v1/settings/tags", user, Map.of("name", "Tag " + UUID.randomUUID(), "color", "cyan"))).isEqualTo(writeTag);
            assertThat(statusOf("GET", "/api/v1/settings/company", user, null)).isEqualTo(readCompany);
            assertThat(statusOf("GET", "/api/v1/offer-templates", user, null)).isEqualTo(readOfferTemplates);
        }

        @Test
        void adminDoesEverything() throws Exception {
            assertThat(statusOf("GET", "/api/v1/settings/company", admin, null)).isEqualTo(200);
            assertThat(statusOf("GET", "/api/v1/settings/rejection-reasons?includeInactive=true", admin, null)).isEqualTo(200);
            assertThat(statusOf("GET", "/api/v1/settings/tags/usage", admin, null)).isEqualTo(200);
        }
    }

    @Nested
    class Seeds {

        @Test
        void stagesMatchTheFrontend() throws Exception {
            JsonNode stages = getJson("/api/v1/settings/stages", admin);

            assertThat(stages).hasSize(7);
            List<String> keys = new ArrayList<>();
            stages.forEach(s -> keys.add(s.path("key").asString()));
            assertThat(keys).containsExactly("new", "screening", "hr_interview", "technical_test", "client_interview", "offer", "hired");
            assertThat(stages.get(0).path("name").asString()).isEqualTo("Nova");
            assertThat(stages.get(0).path("color").asString()).isEqualTo("slate");
            assertThat(stages.get(0).path("autoAction").path("type").asString()).isEqualTo("send_message");
            assertThat(stages.get(0).path("autoAction").path("channel").asString()).isEqualTo("email");
            assertThat(stages.get(1).path("autoAction").path("type").asString()).isEqualTo("none");
            // D9-1: the interview and offer stages have no automatic action by default (their data is unknown on a move)
            assertThat(stages.get(2).path("autoAction").path("type").asString()).isEqualTo("none");
            assertThat(stages.get(4).path("autoAction").path("type").asString()).isEqualTo("none");
            assertThat(stages.get(5).path("autoAction").path("type").asString()).isEqualTo("none");
            assertThat(stages.get(6).path("autoAction").path("type").asString()).isEqualTo("send_message");
        }

        @Test
        void reasonsTagsAndTemplatesMatchTheFrontend() throws Exception {
            JsonNode reasons = getJson("/api/v1/settings/rejection-reasons?includeInactive=true", admin);
            List<String> codes = new ArrayList<>();
            reasons.forEach(r -> codes.add(r.path("code").asString()));
            assertThat(codes).contains("requirements", "client", "withdrew", "declined-offer", "no-show");
            assertThat(jdbc.queryForObject("SELECT label FROM rejection_reasons WHERE code = 'client'", String.class)).isEqualTo("Rejeitado pelo cliente");

            assertThat(jdbc.queryForObject("SELECT count(*) FROM tags WHERE name = 'Experiência offshore' AND color = 'INDIGO'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT code FROM message_templates WHERE is_system ORDER BY code", String.class))
                    .containsExactly("documents-wa", "interview-cancelled-email", "interview-invite-email", "interview-invite-wa", "interview-slots-email",
                            "offer-email", "rejection-email", "test-invite-email");
            assertThat(jdbc.queryForObject("SELECT code FROM offer_templates WHERE is_default", String.class)).isEqualTo("standard");
        }

        @Test
        void seedsHaveNoAuthor() {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM rejection_reasons WHERE created_by IS NOT NULL AND code = 'client'", Integer.class)).isZero();
        }
    }

    @Nested
    class Stages {

        private List<Map<String, Object>> original;

        @BeforeEach
        void snapshot() throws Exception {
            original = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
        }

        @AfterEach
        void restore() throws Exception {
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), original).andExpect(status().isOk());
        }

        @Test
        void renameAndSwapStagesAndTheCacheIsRefreshed() throws Exception {
            List<Map<String, Object>> request = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
            request.get(1).put("name", "Pré-seleção");
            request.get(1).put("order", 3);
            request.get(2).put("order", 2);

            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].key").value("hr_interview"))
                    .andExpect(jsonPath("$[2].name").value("Pré-seleção"));

            JsonNode cached = getJson("/api/v1/settings/stages", admin);
            assertThat(cached.get(2).path("name").asString()).isEqualTo("Pré-seleção");
            assertThat(jdbc.queryForObject("SELECT updated_by FROM pipeline_stages WHERE stage_key = 'SCREENING'", UUID.class)).isEqualTo(admin.id());
        }

        @Test
        void newAndHiredKeepTheirPositions() throws Exception {
            List<Map<String, Object>> request = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
            request.get(0).put("order", 2);
            request.get(1).put("order", 1);

            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("As etapas Nova e Contratado têm posição fixa (primeira e última)."));
        }

        @Test
        void ordersMustBeAPermutationAndEveryStageMustBeSent() throws Exception {
            List<Map<String, Object>> duplicated = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
            duplicated.get(2).put("order", 2);
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), duplicated).andExpect(status().isUnprocessableContent());

            List<Map<String, Object>> incomplete = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
            incomplete.remove(3);
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), incomplete)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Envie todas as etapas do funil, cada uma uma vez."));
        }

        @Test
        void autoActionNeedsAnActiveTemplateOfTheSameChannel() throws Exception {
            UUID smsTemplate = jdbc.queryForObject("SELECT id FROM message_templates WHERE code = 'app-received-sms'", UUID.class);
            List<Map<String, Object>> request = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
            request.get(1).put("autoAction", Map.of("type", "send_message", "templateId", smsTemplate.toString(), "channel", "email"));
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("O canal não corresponde ao do modelo escolhido."));

            request.get(1).put("autoAction", Map.of("type", "send_message", "templateId", UUID.randomUUID().toString()));
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("Escolha um modelo de mensagem ativo."));

            // A template that needs the interview (or offer) data cannot be a stage automatic action (D9-1)
            request.get(1).put("autoAction", Map.of("type", "none"));
            UUID interviewInvite = jdbc.queryForObject("SELECT id FROM message_templates WHERE code = 'interview-invite-wa'", UUID.class);
            request.get(2).put("autoAction", Map.of("type", "send_message", "templateId", interviewInvite.toString()));
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("[2].autoAction.templateId"));
            request.get(2).put("autoAction", Map.of("type", "none"));

            request.get(1).put("autoAction", Map.of("type", "send_message", "templateId", smsTemplate.toString()));
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].autoAction.channel").value("sms"));
        }

        @Test
        void blankNameIs400() throws Exception {
            List<Map<String, Object>> request = StageRequests.toUpdateRequests(getJson("/api/v1/settings/stages", admin));
            request.get(3).put("name", " ");

            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class RejectionReasons {

        @Test
        void createdReasonGetsAStableCodeAndAnAuthor() throws Exception {
            String label = "Documentação incompleta " + UUID.randomUUID().toString().substring(0, 6);
            JsonNode created = JSON.readTree(api.sendJson("POST", "/api/v1/settings/rejection-reasons", users.accessToken(admin), Map.of("label", label, "category", "company"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

            String code = created.path("code").asString();
            assertThat(code).startsWith("documentacao-incompleta-");
            assertThat(created.path("active").asBoolean()).isTrue();

            api.sendJson("PUT", "/api/v1/settings/rejection-reasons/" + created.path("id").asString(), users.accessToken(admin),
                            Map.of("label", "Outro texto", "category", "client", "active", false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.category").value("client"))
                    .andExpect(jsonPath("$.active").value(false));

            assertThat(jdbc.queryForObject("SELECT created_by FROM rejection_reasons WHERE code = ?", UUID.class, code)).isEqualTo(admin.id());
            assertThat(getJson("/api/v1/settings/rejection-reasons", admin).toString()).doesNotContain(code);
            assertThat(getJson("/api/v1/settings/rejection-reasons?includeInactive=true", admin).toString()).contains(code);
        }
    }

    @Nested
    class Tags {

        @Test
        void tagLifecycle() throws Exception {
            String name = "Tag " + UUID.randomUUID().toString().substring(0, 8);
            String token = users.accessToken(admin);
            JsonNode tag = JSON.readTree(api.sendJson("POST", "/api/v1/settings/tags", token, Map.of("name", name, "color", "purple"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            String id = tag.path("id").asString();

            api.sendJson("POST", "/api/v1/settings/tags", token, Map.of("name", name.toUpperCase(), "color", "cyan"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].message").value("Já existe uma tag com este nome."));
            api.sendJson("PUT", "/api/v1/settings/tags/" + id, token, Map.of("name", name + " 2", "color", "danger"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.color").value("danger"));
            assertThat(getJson("/api/v1/settings/tags/usage", admin).path(id).asLong()).isZero();

            api.sendJson("DELETE", "/api/v1/settings/tags/" + id, token, null).andExpect(status().isNoContent());
            assertThat(getJson("/api/v1/settings/tags", admin).toString()).doesNotContain(id);
        }

        @Test
        void unknownColorIs400() throws Exception {
            api.sendJson("POST", "/api/v1/settings/tags", users.accessToken(admin), Map.of("name", "Nova tag", "color", "pink"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("color"));
        }
    }

}
