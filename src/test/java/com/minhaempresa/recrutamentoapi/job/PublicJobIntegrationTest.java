package com.minhaempresa.recrutamentoapi.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class PublicJobIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PUBLIC = "/api/v1/public/jobs";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    AuthApi api;
    TestUser recruiter;
    TestUser interviewer;
    UUID clientId;
    String clientName;
    /** Unique word in the titles of this test, so the shared database does not interfere. */
    String token;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, TestUsers.randomIp());
        recruiter = users.create(UserRole.RECRUITER);
        interviewer = users.create(UserRole.INTERVIEWER);
        clientName = "Petrolífera Teste " + UUID.randomUUID().toString().substring(0, 6);
        clientId = users.createClient(clientName, true);
        token = randomToken();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Consonants only: the Portuguese stemmer never shortens such a word, so prefixes stay prefixes. */
    static String randomToken() {
        String letters = "bcdfghjklmnpqrtvwxz";
        StringBuilder token = new StringBuilder("Kx");
        for (int i = 0; i < 6; i++) {
            token.append(letters.charAt(java.util.concurrent.ThreadLocalRandom.current().nextInt(letters.length())));
        }
        return token.toString();
    }

    JsonNode createJob(String title, String status, Map<String, Object> changes) throws Exception {
        Map<String, Object> body = JobBodies.draft(clientId, recruiter.id(), interviewer.id());
        body.put("title", title);
        body.put("status", status);
        body.putAll(changes);
        ResultActions result = api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), body).andExpect(status().isCreated());
        return json(result);
    }

    JsonNode createOpen(String title) throws Exception {
        return createJob(title, "open", Map.of());
    }

    static JsonNode json(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
        return json(mvc.perform(request).andExpect(status().isOk()));
    }

    static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.path("items").forEach(item -> titles.add(item.path("title").asString()));
        return titles;
    }

    @Test
    void onlyOpenJobsWithinTheDeadlineAreVisible() throws Exception {
        JsonNode open = createOpen("Técnico " + token);
        JsonNode draft = createJob("Rascunho " + token, "draft", Map.of());
        JsonNode paused = createOpen("Pausada " + token);
        api.sendJson("PATCH", "/api/v1/jobs/" + paused.path("id").asString() + "/status", users.accessToken(recruiter), Map.of("status", "paused"))
                .andExpect(status().isOk());
        JsonNode expired = createOpen("Expirada " + token);
        jdbc.update("UPDATE jobs SET deadline = ? WHERE id = ?::uuid", LocalDate.now().minusDays(3), expired.path("id").asString());
        JsonNode noDeadline = createJob("Sem Prazo " + token, "open", new java.util.HashMap<>(java.util.Collections.singletonMap("deadline", null)));

        JsonNode page = search(get(PUBLIC).param("q", token));
        assertThat(titles(page)).containsExactlyInAnyOrder("Técnico " + token, "Sem Prazo " + token);

        mvc.perform(get(PUBLIC + "/" + open.path("slug").asString())).andExpect(status().isOk());
        mvc.perform(get(PUBLIC + "/" + noDeadline.path("slug").asString())).andExpect(status().isOk());
        for (JsonNode hidden : List.of(draft, paused, expired)) {
            mvc.perform(get(PUBLIC + "/" + hidden.path("slug").asString())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NotFoundError"));
        }
    }

    @Test
    void thePublicViewHasNoInternalData() throws Exception {
        JsonNode job = createOpen("Inspetor " + token);
        JsonNode confidential = createJob("Confidencial " + token, "open",
                Map.of("isConfidential", true, "salary", Map.of("min", 1000, "max", 2000, "currency", "USD", "period", "month", "visible", false)));

        JsonNode visible = json(mvc.perform(get(PUBLIC + "/" + job.path("slug").asString())).andExpect(status().isOk()));
        assertThat(visible.path("companyName").asString()).isEqualTo(clientName);
        assertThat(visible.path("salary").path("min").decimalValue()).isEqualByComparingTo("450000");
        for (String internal : List.of("clientId", "recruiterId", "interviewerIds", "testId", "tagIds", "jobRequestId", "version", "closedAt", "createdAt")) {
            assertThat(visible.has(internal)).as(internal).isFalse();
        }
        JsonNode requirement = visible.path("requirements").get(0);
        assertThat(requirement.has("weight")).isFalse();
        assertThat(requirement.has("minValue")).isFalse();
        assertThat(requirement.path("description").asString()).isEqualTo("Curso NEBOSH");
        JsonNode question = visible.path("screeningQuestions").get(0);
        assertThat(question.has("eliminatory")).isFalse();
        assertThat(question.has("expectedAnswer")).isFalse();
        assertThat(question.path("order").asInt()).isEqualTo(1);

        JsonNode hidden = json(mvc.perform(get(PUBLIC + "/" + confidential.path("slug").asString())).andExpect(status().isOk()));
        assertThat(hidden.path("companyName").isNull()).isTrue();
        assertThat(hidden.path("isConfidential").asBoolean()).isTrue();
        assertThat(hidden.path("salary").isNull()).isTrue();
    }

    @Test
    void searchIgnoresAccentsMatchesPrefixesAndFilters() throws Exception {
        createOpen("Logística " + token);
        createJob("Motorista " + token, "open", Map.of("area", "transport", "province", "Luanda", "city", "Viana"));
        createJob("Analista Financeiro " + token, "open", Map.of("area", "finance", "description",
                "<p>Controlo de tesouraria e reconciliações bancárias para a operação logística.</p>"));

        assertThat(titles(search(get(PUBLIC).param("q", "logistica " + token)))).containsExactlyInAnyOrder("Logística " + token, "Analista Financeiro " + token);
        assertThat(titles(search(get(PUBLIC).param("q", "logís " + token.substring(0, 5))))).containsExactlyInAnyOrder("Logística " + token, "Analista Financeiro " + token);
        assertThat(titles(search(get(PUBLIC).param("q", "viana " + token)))).containsExactly("Motorista " + token);
        assertThat(titles(search(get(PUBLIC).param("q", token).param("area", "finance")))).containsExactly("Analista Financeiro " + token);
        assertThat(titles(search(get(PUBLIC).param("q", token).param("province", "Luanda").param("workMode", "onsite")))).containsExactly("Motorista " + token);
        assertThat(titles(search(get(PUBLIC).param("q", token).param("sort", "title"))))
                .containsExactly("Analista Financeiro " + token, "Logística " + token, "Motorista " + token);
        // Symbols only: no text filter instead of an SQL error
        mvc.perform(get(PUBLIC).param("q", "!!! & | :*")).andExpect(status().isOk());
    }

    @Test
    void paginationAndPublicationDate() throws Exception {
        for (int i = 1; i <= 3; i++) {
            createOpen("Auxiliar " + i + " " + token);
        }
        JsonNode old = createOpen("Antiga " + token);
        jdbc.update("UPDATE jobs SET published_at = ? WHERE id = ?::uuid",
                java.sql.Timestamp.from(Instant.now().minus(20, ChronoUnit.DAYS)), old.path("id").asString());

        JsonNode page = search(get(PUBLIC).param("q", token).param("pageSize", "3").param("page", "2"));
        assertThat(page.path("total").asLong()).isEqualTo(4);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("page").asInt()).isEqualTo(2);
        // Newest first: the old one is last
        assertThat(titles(page)).containsExactly("Antiga " + token);

        assertThat(search(get(PUBLIC).param("q", token).param("postedWithinDays", "7")).path("total").asLong()).isEqualTo(3);
        assertThat(search(get(PUBLIC).param("q", token)).path("pageSize").asInt()).isEqualTo(9);
        assertThat(search(get(PUBLIC).param("q", token).param("page", "9")).path("items")).isEmpty();

        mvc.perform(get(PUBLIC).param("pageSize", "51")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pageSize"));
        mvc.perform(get(PUBLIC).param("sort", "random")).andExpect(status().isBadRequest());
    }

    @Test
    void cachedHomePageDataChangesAtMidnightInLuanda() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Luanda")));
        long itBefore = json(mvc.perform(get(PUBLIC + "/area-counts"))).path("it").asLong(0);
        JsonNode lastDay = createJob("Último Dia " + token, "open", Map.of("isFeatured", true, "area", "it", "deadline", today.toString()));

        // Both are cached now (with today's date in the key)
        assertThat(ids(json(mvc.perform(get(PUBLIC + "/featured").param("limit", "12"))))).contains(lastDay.path("id").asString());
        assertThat(json(mvc.perform(get(PUBLIC + "/area-counts"))).path("it").asLong(0)).isEqualTo(itBefore + 1);

        // Midnight passes with no job change: the job leaves without waiting for the cache expiry
        clock.advance(Duration.ofDays(1));
        assertThat(ids(json(mvc.perform(get(PUBLIC + "/featured").param("limit", "12"))))).doesNotContain(lastDay.path("id").asString());
        assertThat(json(mvc.perform(get(PUBLIC + "/area-counts"))).path("it").asLong(0)).isEqualTo(itBefore);
        mvc.perform(get(PUBLIC + "/" + lastDay.path("slug").asString())).andExpect(status().isNotFound());
    }

    @Test
    void renamingTheClientRefreshesTheCachedFeaturedJobs() throws Exception {
        JsonNode featured = createJob("Renomeada " + token, "open", Map.of("isFeatured", true));
        assertThat(companyOf(featured)).isEqualTo(clientName);

        String newName = "Nova Designação " + token;
        TestUser admin = users.create(UserRole.ADMIN);
        api.sendJson("PATCH", "/api/v1/clients/" + clientId, users.accessToken(admin), Map.of("name", newName)).andExpect(status().isOk());

        assertThat(companyOf(featured)).isEqualTo(newName);
    }

    @Test
    void aSearchWithOnlyCommonWordsIgnoresTheTextFilter() throws Exception {
        createJob("Carpinteiro " + token, "open", Map.of("area", "construction"));

        JsonNode withoutText = search(get(PUBLIC).param("area", "construction").param("pageSize", "50"));
        JsonNode onlyStopWords = search(get(PUBLIC).param("q", "de").param("area", "construction").param("pageSize", "50"));

        assertThat(withoutText.path("total").asLong()).isPositive();
        assertThat(onlyStopWords.path("total").asLong()).isEqualTo(withoutText.path("total").asLong());
        assertThat(titles(onlyStopWords)).isEqualTo(titles(withoutText));
        // A real word next to the stop word still filters
        assertThat(titles(search(get(PUBLIC).param("q", "de " + token)))).containsExactly("Carpinteiro " + token);
    }

    /** {@code companyName} of the job in the (cached) featured list. */
    String companyOf(JsonNode job) throws Exception {
        for (JsonNode item : json(mvc.perform(get(PUBLIC + "/featured").param("limit", "12")).andExpect(status().isOk()))) {
            if (item.path("id").asString().equals(job.path("id").asString())) {
                return item.path("companyName").asString();
            }
        }
        throw new AssertionError("job not featured");
    }

    static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    @Test
    void featuredAndAreaCountsAreCachedButFollowChanges() throws Exception {
        JsonNode countsBefore = json(mvc.perform(get(PUBLIC + "/area-counts")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public")));
        long maintenanceBefore = countsBefore.path("maintenance").asLong(0);
        mvc.perform(get(PUBLIC + "/featured")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"));

        JsonNode featured = createJob("Destaque " + token, "open", Map.of("isFeatured", true, "area", "maintenance"));

        JsonNode countsAfter = json(mvc.perform(get(PUBLIC + "/area-counts")).andExpect(status().isOk()));
        assertThat(countsAfter.path("maintenance").asLong()).isEqualTo(maintenanceBefore + 1);
        JsonNode list = json(mvc.perform(get(PUBLIC + "/featured").param("limit", "12")).andExpect(status().isOk()));
        assertThat(list.get(0).path("id").asString()).isEqualTo(featured.path("id").asString());
        assertThat(list.get(0).has("clientId")).isFalse();

        api.sendJson("PATCH", "/api/v1/jobs/" + featured.path("id").asString() + "/status", users.accessToken(recruiter), Map.of("status", "closed"))
                .andExpect(status().isOk());
        JsonNode afterClose = json(mvc.perform(get(PUBLIC + "/featured").param("limit", "12")).andExpect(status().isOk()));
        afterClose.forEach(job -> assertThat(job.path("id").asString()).isNotEqualTo(featured.path("id").asString()));
        assertThat(json(mvc.perform(get(PUBLIC + "/area-counts"))).path("maintenance").asLong(0)).isEqualTo(maintenanceBefore);

        mvc.perform(get(PUBLIC + "/featured").param("limit", "13")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Indique entre 1 e 12 vagas."));
    }
}
