package com.minhaempresa.recrutamentoapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class ContactRequestIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PUBLIC = "/api/v1/public/contact-requests";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MailpitClient mailpit;

    TestUser admin;

    @BeforeEach
    void setUp() {
        // Every test only reads the emails of the administrator it creates
        mailpit.deleteAll();
        admin = users.create(UserRole.ADMIN);
    }

    /** Body as the frontend ContactForm sends it (no login). */
    static Map<String, Object> companyRequest(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", "company");
        body.put("name", "Joana Baptista");
        body.put("email", email);
        body.put("phone", "923 111 222");
        body.put("message", "Precisamos de 5 técnicos de manutenção para o Soyo.");
        body.put("company", "Manutenção Soyo Lda");
        body.put("vacancies", 5);
        return body;
    }

    ResultActions send(Map<String, Object> body, RequestPostProcessor ip) throws Exception {
        return mvc.perform(post(PUBLIC).with(ip).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    ResultActions send(Map<String, Object> body) throws Exception {
        return send(body, TestUsers.randomIp());
    }

    @Test
    void companyRequestIsStoredAndTheAdministratorsAreTold() throws Exception {
        String email = TestUsers.uniqueEmail();
        send(companyRequest(email.toUpperCase())).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM contact_requests WHERE email = ?", email);
        assertThat(row.get("kind")).isEqualTo("COMPANY");
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("phone")).isEqualTo("+244923111222");
        assertThat(row.get("company")).isEqualTo("Manutenção Soyo Lda");
        assertThat(row.get("vacancies")).isEqualTo(5);
        assertThat(row.get("created_by")).isNull();
        assertThat(row).doesNotContainKeys("ip", "ip_address");

        Email notice = mailpit.awaitEmailTo(admin.email());
        assertThat(notice.subject()).isEqualTo("Nova empresa interessada");
        assertThat(notice.text()).contains("Manutenção Soyo Lda").contains("5 pessoa(s)").contains(email).contains("Soyo");
    }

    @Test
    void generalRequestKeepsNoCompanyData() throws Exception {
        String email = TestUsers.uniqueEmail();
        Map<String, Object> body = companyRequest(email);
        body.put("kind", "general");
        body.put("company", "");
        send(body).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM contact_requests WHERE email = ?", email);
        assertThat(row.get("company")).isNull();
        assertThat(row.get("vacancies")).isNull();
        assertThat(mailpit.awaitEmailTo(admin.email()).subject()).isEqualTo("Nova mensagem de contacto");
    }

    @Test
    void validationFollowsTheContactForm() throws Exception {
        Map<String, Object> body = companyRequest(TestUsers.uniqueEmail());
        body.put("company", "X");
        body.put("vacancies", 501);
        body.put("message", "Curta");
        send(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'company')].message").value("Indique o nome da empresa."))
                .andExpect(jsonPath("$.errors[?(@.field == 'vacancies')].message").value("Para mais de 500 vagas, contacte-nos diretamente."))
                .andExpect(jsonPath("$.errors[?(@.field == 'message')].message").value("Escreva uma mensagem com pelo menos 10 caracteres."));

        Map<String, Object> noVacancies = companyRequest(TestUsers.uniqueEmail());
        noVacancies.remove("vacancies");
        send(noVacancies).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("vacancies"))
                .andExpect(jsonPath("$.errors[0].message").value("Indique pelo menos 1 vaga."));

        Map<String, Object> person = companyRequest(TestUsers.uniqueEmail());
        person.put("name", "Joana");
        person.put("phone", "12345");
        person.put("message", "x".repeat(2001));
        send(person).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'message')].message").value("A mensagem é demasiado longa."));
    }

    @Test
    void honeypotRequestsAreDiscardedWithoutAnySign() throws Exception {
        String robot = TestUsers.uniqueEmail();
        Map<String, Object> body = companyRequest(robot);
        body.put("website", "http://spam.example");
        send(body).andExpect(status().isNoContent());

        String human = TestUsers.uniqueEmail();
        send(companyRequest(human)).andExpect(status().isNoContent());
        mailpit.awaitEmailTo(admin.email());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_requests WHERE email = ?", Integer.class, robot)).isZero();
        assertThat(mailpit.countTo(admin.email())).isEqualTo(1);
    }

    @Test
    void perEmailLimitCannotBeBypassedByChangingIp() throws Exception {
        String email = TestUsers.uniqueEmail();
        RequestPostProcessor ipA = TestUsers.fromIp("10.250.1.1");
        RequestPostProcessor ipB = TestUsers.fromIp("10.250.2.2");
        for (int i = 0; i < 3; i++) {
            send(companyRequest(email), ipA).andExpect(status().isNoContent());
        }
        // 4th request of the same email from another IP (whose own per-IP count is still 0)
        send(companyRequest(email), ipB)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("AuthError"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_requests WHERE email = ?", Integer.class, email)).isEqualTo(3);
    }

    @Test
    void perIpLimitIsConfigurable() throws Exception {
        RequestPostProcessor ip = TestUsers.randomIp();
        // application-test.yml: 5 per IP
        for (int i = 0; i < 5; i++) {
            send(companyRequest(TestUsers.uniqueEmail()), ip).andExpect(status().isNoContent());
        }
        send(companyRequest(TestUsers.uniqueEmail()), ip).andExpect(status().isTooManyRequests());
        send(companyRequest(TestUsers.uniqueEmail())).andExpect(status().isNoContent());
    }

    @Test
    void administratorsFollowUpTheRequests() throws Exception {
        String email = TestUsers.uniqueEmail();
        send(companyRequest(email)).andExpect(status().isNoContent());
        UUID id = jdbc.queryForObject("SELECT id FROM contact_requests WHERE email = ?", UUID.class, email);
        AuthApi api = new AuthApi(mvc);

        mvc.perform(get("/api/v1/contact-requests").param("kind", "company").param("status", "new").param("pageSize", "5").with(users.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.items[0].kind").value("company"));

        api.sendJson("PATCH", "/api/v1/contact-requests/" + id + "/status", users.accessToken(admin), Map.of("status", "closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
        assertThat(jdbc.queryForObject("SELECT closed_at FROM contact_requests WHERE id = ?", Object.class, id)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT updated_by FROM contact_requests WHERE id = ?", UUID.class, id)).isEqualTo(admin.id());

        api.sendJson("PATCH", "/api/v1/contact-requests/" + id + "/status", users.accessToken(admin), Map.of("status", "contacted"))
                .andExpect(jsonPath("$.status").value("contacted"));
        assertThat(jdbc.queryForObject("SELECT closed_at FROM contact_requests WHERE id = ?", Object.class, id)).isNull();

        TestUser recruiter = users.create(UserRole.RECRUITER);
        mvc.perform(get("/api/v1/contact-requests").with(users.bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/contact-requests")).andExpect(status().isUnauthorized());
    }
}
