package com.minhaempresa.recrutamentoapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client A versus client B on every client-portal endpoint: a client user only ever sees its own
 * company (from the access token); another company's records are "not found" (404, same body as an id
 * that does not exist). {@link #everyPortalEndpointIsCoveredByThisTest()} fails when a new portal
 * endpoint is added without being added here.
 */
@IntegrationTest
class ClientPortalIsolationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String BASE = "/api/v1/client-portal";

    /** Every portal endpoint ("METHOD path") checked by this class. */
    static final Set<String> COVERED = Set.of(
            "GET " + BASE + "/company",
            "GET " + BASE + "/job-requests",
            "GET " + BASE + "/job-requests/{id}",
            "POST " + BASE + "/job-requests");

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    AuthApi api;
    UUID clientA;
    UUID clientB;
    TestUser userA;
    TestUser userB;
    String requestOfA;
    String requestOfB;

    @BeforeEach
    void setUp() throws Exception {
        api = new AuthApi(mvc, TestUsers.randomIp());
        // Known state: job requests email every administrator; none of the earlier tests' admins
        users.deactivateTestAdministrators();
        clientA = users.createClient("Empresa A " + UUID.randomUUID().toString().substring(0, 4), true);
        clientB = users.createClient("Empresa B " + UUID.randomUUID().toString().substring(0, 4), true);
        userA = users.createClientUser(clientA);
        userB = users.createClientUser(clientB);
        requestOfA = createRequest(userA, Map.of()).path("id").asString();
        requestOfB = createRequest(userB, Map.of()).path("id").asString();
    }

    JsonNode createRequest(TestUser user, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = JobRequestIntegrationTest.validRequest();
        body.putAll(extra);
        String json = api.sendJson("POST", BASE + "/job-requests", users.accessToken(user), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json);
    }

    @Test
    void everyPortalEndpointIsCoveredByThisTest() {
        Set<String> registered = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(path -> path.startsWith(BASE))
                .forEach(path -> info.getMethodsCondition().getMethods().forEach(method -> registered.add(method.name() + " " + path))));

        assertThat(registered).isEqualTo(new TreeSet<>(COVERED));
    }

    @Test
    void companyIsTheOneInTheToken() throws Exception {
        String nameB = jdbc.queryForObject("SELECT name FROM clients WHERE id = ?", String.class, clientB);
        mvc.perform(get(BASE + "/company").with(users.bearer(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientB.toString()))
                .andExpect(jsonPath("$.name").value(nameB));
    }

    @Test
    void listShowsOnlyTheOwnCompanyRequests() throws Exception {
        JsonNode list = JSON.readTree(mvc.perform(get(BASE + "/job-requests").with(users.bearer(userB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("id").asString()).isEqualTo(requestOfB);
        list.forEach(item -> assertThat(item.path("clientId").asString()).isEqualTo(clientB.toString()));
    }

    @Test
    void anotherCompanyRequestIsNotFoundLikeAMissingOne() throws Exception {
        String other = mvc.perform(get(BASE + "/job-requests/" + requestOfA).with(users.bearer(userB)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get(BASE + "/job-requests/" + UUID.randomUUID()).with(users.bearer(userB)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        JsonNode otherBody = JSON.readTree(other);
        JsonNode missingBody = JSON.readTree(missing);
        assertThat(otherBody.path("error").asString()).isEqualTo(missingBody.path("error").asString()).isEqualTo("NotFoundError");
        assertThat(otherBody.path("message").asString()).isEqualTo(missingBody.path("message").asString()).isEqualTo("Pedido de vaga não encontrado.");
        // The owner still sees it
        mvc.perform(get(BASE + "/job-requests/" + requestOfA).with(users.bearer(userA))).andExpect(status().isOk());
    }

    @Test
    void createIgnoresTheCompanyAndRequesterSentInTheBody() throws Exception {
        JsonNode created = createRequest(userB, Map.of("clientId", clientA.toString(), "requestedById", userA.id().toString()));

        assertThat(created.path("clientId").asString()).isEqualTo(clientB.toString());
        assertThat(created.path("requestedById").asString()).isEqualTo(userB.id().toString());
        assertThat(jdbc.queryForObject("SELECT client_id FROM job_requests WHERE id = ?::uuid", UUID.class, created.path("id").asString())).isEqualTo(clientB);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_requests WHERE client_id = ?", Integer.class, clientA)).isEqualTo(1);
    }

    @Test
    void clientUsersCannotUseTheInternalEndpoints() throws Exception {
        String token = users.accessToken(userB);
        for (String path : List.of("/api/v1/clients", "/api/v1/clients/options", "/api/v1/clients/" + clientB,
                "/api/v1/job-requests", "/api/v1/job-requests/" + requestOfB, "/api/v1/contact-requests", "/api/v1/users")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        api.sendJson("PATCH", "/api/v1/job-requests/" + requestOfA + "/review", token, Map.of("status", "rejected", "note", "Recusado pelo B"))
                .andExpect(status().isForbidden());
        api.sendJson("PATCH", "/api/v1/clients/" + clientA, token, Map.of("status", "inactive")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM job_requests WHERE id = ?::uuid", String.class, requestOfA)).isEqualTo("PENDING");
    }

    @Test
    void staffAndCandidatesCannotUseThePortal() throws Exception {
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.INTERVIEWER, UserRole.CANDIDATE)) {
            TestUser user = users.create(role);
            for (String path : List.of(BASE + "/company", BASE + "/job-requests", BASE + "/job-requests/" + requestOfA)) {
                mvc.perform(get(path).with(users.bearer(user))).andExpect(status().isForbidden());
            }
            api.sendJson("POST", BASE + "/job-requests", users.accessToken(user), JobRequestIntegrationTest.validRequest())
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousCallsAre401() throws Exception {
        mvc.perform(get(BASE + "/job-requests")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/company")).andExpect(status().isUnauthorized());
    }
}
