package com.minhaempresa.recrutamentoapi.messaging;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class MessageTemplateIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    TestUser recruiter;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        recruiter = users.create(UserRole.RECRUITER);
    }

    private static Map<String, Object> template(String channel, String subject, String body) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Modelo " + UUID.randomUUID().toString().substring(0, 6));
        request.put("channel", channel);
        request.put("category", "general");
        request.put("subject", subject);
        request.put("body", body);
        return request;
    }

    @Test
    void recruiterCreatesAndEditsTemplates() throws Exception {
        JsonNode created = JSON.readTree(api.sendJson("POST", "/api/v1/message-templates", users.accessToken(recruiter),
                        template("email", "Olá {{nome}}", "Olá {{nome}},\nobrigado pela candidatura a {{vaga}}."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false))
                .andReturn().getResponse().getContentAsString());

        api.sendJson("PUT", "/api/v1/message-templates/" + created.path("id").asString(), users.accessToken(recruiter),
                        template("sms", null, "FEH OIRAM: {{nome}}, temos novidades sobre {{vaga}}."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("sms"))
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andExpect(jsonPath("$.code").value(created.path("code").asString()));

        assertThat(jdbc.queryForObject("SELECT created_by FROM message_templates WHERE id = ?::uuid", UUID.class, created.path("id").asString())).isEqualTo(recruiter.id());
    }

    @Test
    void frontendRulesAreEnforced() throws Exception {
        String token = users.accessToken(recruiter);

        api.sendJson("POST", "/api/v1/message-templates", token, template("sms", null, "x".repeat(321)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].message").value("O SMS tem no máximo 320 caracteres."));
        api.sendJson("POST", "/api/v1/message-templates", token, template("email", null, "Corpo do email com texto suficiente."))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        api.sendJson("POST", "/api/v1/message-templates", token, template("whatsapp", null, "Olá {{nome}}, a sua {{senha}} está pronta."))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Variável desconhecida: {{senha}}."));
    }

    @Test
    void htmlIsRejected() throws Exception {
        api.sendJson("POST", "/api/v1/message-templates", users.accessToken(recruiter),
                        template("email", "Assunto válido", "Clique <a href=\"https://mau.example\">aqui</a> para continuar."))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Os modelos são texto simples: não use etiquetas HTML."));
    }

    @Test
    void deletingDeactivatesAndSwitchesOffStageActions() throws Exception {
        TestUser admin = users.create(UserRole.ADMIN);
        JsonNode created = JSON.readTree(api.sendJson("POST", "/api/v1/message-templates", users.accessToken(recruiter),
                        template("whatsapp", null, "Olá {{nome}}, temos uma atualização sobre {{vaga}}."))
                .andReturn().getResponse().getContentAsString());
        String templateId = created.path("id").asString();
        JsonNode stages = JSON.readTree(mvc.perform(get("/api/v1/settings/stages").with(users.bearer(admin))).andReturn().getResponse().getContentAsString());
        List<Map<String, Object>> original = StageRequests.toUpdateRequests(stages);
        List<Map<String, Object>> request = StageRequests.toUpdateRequests(stages);
        request.get(1).put("autoAction", Map.of("type", "send_message", "templateId", templateId));
        api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), request).andExpect(status().isOk());

        try {
            api.sendJson("DELETE", "/api/v1/message-templates/" + templateId, users.accessToken(recruiter), null).andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/settings/stages").with(users.bearer(admin)))
                    .andExpect(jsonPath("$[1].autoAction.type").value("none"));
            String list = mvc.perform(get("/api/v1/message-templates").with(users.bearer(recruiter))).andReturn().getResponse().getContentAsString();
            assertThat(list).doesNotContain(templateId);
            assertThat(jdbc.queryForObject("SELECT active FROM message_templates WHERE id = ?::uuid", Boolean.class, templateId)).isFalse();
        } finally {
            api.sendJson("PUT", "/api/v1/settings/stages", users.accessToken(admin), original).andExpect(status().isOk());
        }
    }

    @Test
    void systemTemplatesCannotBeDeleted() throws Exception {
        UUID rejection = jdbc.queryForObject("SELECT id FROM message_templates WHERE code = 'rejection-email'", UUID.class);

        api.sendJson("DELETE", "/api/v1/message-templates/" + rejection, users.accessToken(recruiter), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Este modelo é usado pelo sistema e não pode ser apagado."));
    }

    @Test
    void interviewerReadsButCannotWrite() throws Exception {
        TestUser interviewer = users.create(UserRole.INTERVIEWER);

        mvc.perform(get("/api/v1/message-templates").param("channel", "email").with(users.bearer(interviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].channel").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("email"))));
        api.sendJson("POST", "/api/v1/message-templates", users.accessToken(interviewer), template("sms", null, "Olá {{nome}}, tudo bem?"))
                .andExpect(status().isForbidden());
    }
}
