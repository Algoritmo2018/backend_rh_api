package com.minhaempresa.recrutamentoapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class JobRequestIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PORTAL = "/api/v1/client-portal/job-requests";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MailpitClient mailpit;

    AuthApi api;
    TestUser admin;
    UUID clientId;
    TestUser clientUser;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, TestUsers.randomIp());
        // Every test only reads the emails of the users it creates
        mailpit.deleteAll();
        admin = users.create(UserRole.ADMIN);
        clientId = users.createClient("Logística Atlântico " + UUID.randomUUID().toString().substring(0, 4), true);
        clientUser = users.createClientUser(clientId);
    }

    /** Body as the frontend sends it (clientId/requestedById are ignored), with the area key. */
    static Map<String, Object> validRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("clientId", UUID.randomUUID().toString());
        body.put("requestedById", UUID.randomUUID().toString());
        body.put("title", "Motorista de pesados");
        body.put("area", "logistics");
        body.put("province", "Benguela");
        body.put("vacancies", 3);
        body.put("description", "Condução de camiões entre o Lobito e Luanda.");
        body.put("requirements", "Carta de condução C e 3 anos de experiência.");
        body.put("salaryBudget", Map.of("amount", 450000, "currency", "AOA"));
        body.put("desiredStartDate", LocalDate.now().plusMonths(2).toString());
        body.put("urgency", "high");
        return body;
    }

    ResultActions submit(Map<String, Object> body) throws Exception {
        return api.sendJson("POST", PORTAL, users.accessToken(clientUser), body);
    }

    JsonNode create() throws Exception {
        return JSON.readTree(submit(validRequest()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    ResultActions review(String id, String status, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("note", note);
        return api.sendJson("PATCH", "/api/v1/job-requests/" + id + "/review", users.accessToken(admin), body);
    }

    @Test
    void clientCreatesARequestAndTheAdministratorsAreTold() throws Exception {
        Map<String, Object> body = validRequest();
        JsonNode created = JSON.readTree(submit(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(created.path("status").asString()).isEqualTo("pending");
        assertThat(created.path("clientId").asString()).isEqualTo(clientId.toString());
        assertThat(created.path("requestedById").asString()).isEqualTo(clientUser.id().toString());
        assertThat(created.path("requestedByName").asString()).startsWith("Cliente Teste");
        assertThat(created.path("clientName").asString()).startsWith("Logística Atlântico");
        assertThat(created.path("area").asString()).isEqualTo("logistics");
        assertThat(created.path("province").asString()).isEqualTo("Benguela");
        assertThat(created.path("desiredStartDate").asString()).isEqualTo(body.get("desiredStartDate"));
        assertThat(created.path("salaryBudget").path("currency").asString()).isEqualTo("AOA");
        assertThat(created.path("salaryBudget").path("amount").decimalValue()).isEqualByComparingTo("450000");
        assertThat(created.path("urgency").asString()).isEqualTo("high");
        assertThat(created.path("jobId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_by FROM job_requests WHERE id = ?::uuid", UUID.class, created.path("id").asString()))
                .isEqualTo(clientUser.id());

        Email email = mailpit.awaitEmailTo(admin.email());
        assertThat(email.subject()).isEqualTo("Novo pedido de vaga");
        assertThat(email.text()).contains("Motorista de pesados").contains("/app/clientes/" + clientId + "?separador=pedidos");
    }

    @Test
    void theAreaIsOnlyTheEnumKey() throws Exception {
        Map<String, Object> body = validRequest();
        body.put("area", "Logística e Armazém");
        submit(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("ValidationError"));
    }

    @Test
    void validationFollowsTheFrontend() throws Exception {
        Map<String, Object> body = validRequest();
        body.put("vacancies", 201);
        body.put("title", "Mo");
        body.put("description", "Curta demais");
        body.put("salaryBudget", Map.of("amount", 0, "currency", "AOA"));
        submit(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(4));

        Map<String, Object> noBudget = validRequest();
        noBudget.remove("salaryBudget");
        noBudget.remove("desiredStartDate");
        JsonNode created = JSON.readTree(submit(noBudget).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.path("salaryBudget").isNull()).isTrue();
        assertThat(created.path("desiredStartDate").isNull()).isTrue();
    }

    @Test
    void theStartDateCannotBeInThePast() throws Exception {
        Map<String, Object> body = validRequest();
        body.put("desiredStartDate", "2020-01-01");
        submit(body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("desiredStartDate"))
                .andExpect(jsonPath("$.errors[0].message").value("A data não pode ser no passado."));
    }

    @Test
    void administratorRejectsWithAReasonAndTheClientIsTold() throws Exception {
        String id = create().path("id").asString();

        review(id, "rejected", "Curto").andExpect(status().isOk());
        // (5 characters is the minimum, like the frontend; the request is now rejected)
        String other = create().path("id").asString();
        review(other, "rejected", "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("note"))
                .andExpect(jsonPath("$.errors[0].message").value("Indique o motivo (5 a 1000 caracteres)."));
        review(other, "approved", "Aprovado pela direção")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        review(other, "converted", "Vaga criada já")
                .andExpect(status().isUnprocessableContent());

        review(other, "rejected", "O orçamento está abaixo do mercado.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.reviewNote").value("O orçamento está abaixo do mercado."));
        review(other, "rejected", "Outra vez recusado")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Este pedido já foi analisado."));

        // The client sees the reason in the portal and gets an email
        mvc.perform(get(PORTAL + "/" + other).with(users.bearer(clientUser)))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.reviewNote").value("O orçamento está abaixo do mercado."));
        Email email = mailpit.awaitEmailTo(clientUser.email(), 2);
        assertThat(email.subject()).isEqualTo("Pedido de vaga recusado");
        assertThat(email.text()).contains("/cliente/pedir-vaga");
    }

    @Test
    void conversionIsDoneByTheJobsModuleAndTellsTheClient() throws Exception {
        String id = create().path("id").asString();
        TestUser recruiter = users.create(UserRole.RECRUITER);
        Map<String, Object> job = JobBodies.draft(UUID.randomUUID(), recruiter.id(), users.create(UserRole.INTERVIEWER).id());
        job.put("jobRequestId", id);

        // The jobs module converts the request in the transaction that creates the job
        JsonNode created = JSON.readTree(api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), job)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String jobId = created.path("id").asString();
        assertThat(created.path("clientId").asString()).isEqualTo(clientId.toString());

        mvc.perform(get("/api/v1/job-requests/" + id).with(users.bearer(admin)))
                .andExpect(jsonPath("$.status").value("converted"))
                .andExpect(jsonPath("$.jobId").value(jobId));
        Email email = mailpit.awaitEmailTo(clientUser.email());
        assertThat(email.subject()).isEqualTo("Pedido de vaga aceite");
        review(id, "rejected", "Tarde demais para recusar").andExpect(status().isUnprocessableContent());
    }

    @Test
    void staffListsAndFiltersButOnlyAdministratorsReview() throws Exception {
        String id = create().path("id").asString();
        TestUser recruiter = users.create(UserRole.RECRUITER);

        mvc.perform(get("/api/v1/job-requests").param("clientId", clientId.toString()).with(users.bearer(recruiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/v1/job-requests").param("clientId", clientId.toString()).param("status", "rejected").with(users.bearer(recruiter)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/job-requests/" + id).with(users.bearer(recruiter))).andExpect(status().isOk());
        api.sendJson("PATCH", "/api/v1/job-requests/" + id + "/review", users.accessToken(recruiter), Map.of("status", "rejected", "note", "Sem orçamento"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/job-requests").with(users.bearer(users.create(UserRole.INTERVIEWER)))).andExpect(status().isForbidden());
    }
}
