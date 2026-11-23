package com.minhaempresa.recrutamentoapi.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class OfferTemplateIntegrationTest {

    private static final String BODY = "Exmo(a). Sr(a). {{nome_completo}},\n\nPropomos a função de {{cargo}} com o salário de {{salario}}.";

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

    @Test
    void onlyOneTemplateIsTheDefault() throws Exception {
        String name = "Proposta " + UUID.randomUUID().toString().substring(0, 6);
        String id = JsonMapper.builder().build().readTree(api.sendJson("POST", "/api/v1/offer-templates", users.accessToken(admin), Map.of("name", name, "body", BODY, "isDefault", true))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.isDefault").value(true))
                        .andReturn().getResponse().getContentAsString())
                .path("id").asString();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM offer_templates WHERE is_default", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_default FROM offer_templates WHERE code = 'standard'", Boolean.class)).isFalse();

        // Back to the original default
        UUID standard = jdbc.queryForObject("SELECT id FROM offer_templates WHERE code = 'standard'", UUID.class);
        api.sendJson("PUT", "/api/v1/offer-templates/" + standard, users.accessToken(admin),
                        Map.of("name", "Proposta padrão", "body", jdbc.queryForObject("SELECT body FROM offer_templates WHERE code = 'standard'", String.class), "isDefault", true))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT is_default FROM offer_templates WHERE id = ?::uuid", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM offer_templates WHERE is_default", Integer.class)).isEqualTo(1);
    }

    @Test
    void variablesAndPlainTextAreChecked() throws Exception {
        api.sendJson("POST", "/api/v1/offer-templates", users.accessToken(admin), Map.of("name", "Proposta X", "body", BODY + " {{bonus}}", "isDefault", false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Variável desconhecida: {{bonus}}."));
        api.sendJson("POST", "/api/v1/offer-templates", users.accessToken(admin), Map.of("name", "Proposta X", "body", "<p>" + BODY + "</p>", "isDefault", false))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void recruiterReadsButOnlyAdminWrites() throws Exception {
        TestUser recruiter = users.create(UserRole.RECRUITER);

        mvc.perform(get("/api/v1/offer-templates").with(users.bearer(recruiter))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        api.sendJson("POST", "/api/v1/offer-templates", users.accessToken(recruiter), Map.of("name", "Proposta Y", "body", BODY, "isDefault", false))
                .andExpect(status().isForbidden());
    }
}
