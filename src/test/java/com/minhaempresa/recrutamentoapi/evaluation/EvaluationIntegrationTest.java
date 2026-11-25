package com.minhaempresa.recrutamentoapi.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureService;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.Bell;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Stage 9b: evaluations (scorecards) — pending on done, submit, blind visibility, free evaluation, reopen, erasure. */
@IntegrationTest
class EvaluationIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CandidateErasureService erasureService;

    AuthApi api;
    TestUser admin;
    TestUser recruiter;
    TestUser first;
    TestUser second;
    TestUser outsider;
    UUID clientId;

    record Job(UUID id, String title, String question) {}

    record App(UUID id, UUID candidateId) {}

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        admin = users.create(UserRole.ADMIN);
        recruiter = users.create(UserRole.RECRUITER);
        first = users.create(UserRole.INTERVIEWER);
        second = users.create(UserRole.INTERVIEWER);
        outsider = users.create(UserRole.INTERVIEWER);
        clientId = users.createClient();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    Job openJob() throws Exception {
        // No interviewer assigned to the job: the evaluators reach the application through their interview only
        Map<String, Object> body = JobBodies.draft(clientId, recruiter.id(), recruiter.id());
        String title = "Soldador " + UUID.randomUUID().toString().substring(0, 6);
        body.put("title", title);
        body.put("status", "open");
        JsonNode job = json(send("POST", "/api/v1/jobs", recruiter, body).andExpect(status().isCreated()));
        return new Job(UUID.fromString(job.path("id").asString()), title, job.path("screeningQuestions").get(0).path("id").asString());
    }

    App apply(Job job) throws Exception {
        String email = TestUsers.uniqueEmail();
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", job.id().toString());
        body.put("fullName", "Helena Avaliada Teste");
        body.put("email", email);
        body.put("phone", TestUsers.uniqueLocalPhone());
        body.put("province", "Luanda");
        body.put("screeningAnswers", List.of(Map.of("questionId", job.question(), "answer", "sim")));
        body.put("consentJob", true);
        body.put("consentTalentPool", false);
        mvc.perform(multipart("/api/v1/public/applications")
                        .file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(body)))
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", SampleFiles.pdf()))
                        .with(TestUsers.randomIp()))
                .andExpect(status().isAccepted());
        Map<String, Object> row = jdbc.queryForMap("SELECT id, candidate_id FROM applications WHERE job_id = ? AND email = ?", job.id(), email);
        return new App((UUID) row.get("id"), (UUID) row.get("candidate_id"));
    }

    /** An interview of the application with these interviewers, held and marked {@code done}; returns its id. */
    String doneInterview(App app, TestUser... interviewers) throws Exception {
        Instant startsAt = clock.instant().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(9));
        Map<String, Object> body = new HashMap<>();
        body.put("applicationId", app.id().toString());
        body.put("type", "technical");
        body.put("mode", "phone");
        body.put("startsAt", startsAt.toString());
        body.put("durationMinutes", 30);
        body.put("interviewerIds", Arrays.stream(interviewers).map(u -> u.id().toString()).toList());
        body.put("notifyCandidate", false);
        JsonNode interview = json(send("POST", "/api/v1/interviews", recruiter, body).andExpect(status().isCreated()));
        clock.advance(Duration.ofDays(2));
        String id = interview.path("id").asString();
        send("PATCH", "/api/v1/interviews/" + id + "/status", recruiter, Map.of("status", "done", "version", interview.path("version").asLong()))
                .andExpect(status().isOk());
        return id;
    }

    String pendingOf(TestUser evaluator, String interviewId) {
        return jdbc.queryForObject("SELECT id::text FROM evaluations WHERE interview_id = ?::uuid AND evaluator_id = ?", String.class, interviewId, evaluator.id());
    }

    static Map<String, Object> scorecard(int... scores) {
        List<Map<String, Object>> lines = new ArrayList<>();
        String[] names = {"Conhecimento técnico", "Comunicação", "Trabalho em equipa", "Motivação"};
        for (int i = 0; i < scores.length; i++) {
            lines.add(Map.of("competency", names[i], "score", scores[i], "comment", "Comentário " + i));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("scores", lines);
        body.put("recommendation", "yes");
        body.put("strengths", "Boa experiência em soldadura TIG.");
        body.put("concerns", "Pouca experiência offshore.");
        body.put("notes", "Pediu salário acima da média.");
        return body;
    }

    ResultActions send(String method, String url, TestUser user, Object body) throws Exception {
        return api.sendJson(method, url, users.accessToken(user), body);
    }

    ResultActions read(String url, TestUser user) throws Exception {
        return mvc.perform(get(url).with(users.bearer(user)));
    }

    static JsonNode json(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? JSON.missingNode() : JSON.readTree(content);
    }

    // =====================================================================================

    @Test
    void aDoneInterviewGivesEachInterviewerAPendingEvaluationAndABell() throws Exception {
        Job job = openJob();
        App app = apply(job);
        String interviewId = doneInterview(app, first, second);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluations WHERE interview_id = ?::uuid AND status = 'PENDING'", Integer.class, interviewId))
                .isEqualTo(2);
        JsonNode mine = json(read("/api/v1/evaluations/mine?status=pending", first).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("status").asString()).isEqualTo("pending");
        assertThat(mine.get(0).path("candidateName").asString()).isEqualTo("Helena Avaliada Teste");
        assertThat(mine.get(0).path("jobTitle").asString()).isEqualTo(job.title());
        assertThat(mine.get(0).path("interview").path("id").asString()).isEqualTo(interviewId);
        assertThat(mine.get(0).path("interview").path("type").asString()).isEqualTo("technical");
        assertThat(mine.get(0).path("overallScore").isNull()).isTrue();
        assertThat(json(read("/api/v1/evaluations/mine?status=submitted", first))).isEmpty();
        assertThat(Bell.of(jdbc, first.id()).getFirst()).satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("EVALUATION_REQUESTED");
            assertThat(bell.title()).isEqualTo("Avaliação por preencher");
            assertThat(bell.body()).contains("Helena Avaliada Teste").contains(job.title());
            assertThat(bell.link()).isEqualTo("/app/minhas-avaliacoes");
        });
    }

    @Test
    void onlyTheEvaluatorSubmitsAtLeastOneScoreAboveZeroAndASubmittedEvaluationIsFinal() throws Exception {
        App app = apply(openJob());
        String interviewId = doneInterview(app, first, second);
        String evaluationId = pendingOf(first, interviewId);
        String submit = "/api/v1/evaluations/" + evaluationId + "/submit";

        send("POST", submit, second, scorecard(4)).andExpect(status().isForbidden());
        send("POST", submit, recruiter, scorecard(4)).andExpect(status().isForbidden());
        send("POST", submit, first, scorecard(0, 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Avalie pelo menos uma competência."))
                .andExpect(jsonPath("$.errors[0].field").value("scores"));
        send("POST", submit, first, scorecard(6)).andExpect(status().isBadRequest());
        Map<String, Object> noRecommendation = scorecard(3);
        noRecommendation.remove("recommendation");
        send("POST", submit, first, noRecommendation).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Escolha uma recomendação."));

        // 0 = not evaluated: (4 + 5) / 2 = 4.5
        JsonNode submitted = json(send("POST", submit, first, scorecard(4, 0, 5)).andExpect(status().isOk()));
        assertThat(submitted.path("status").asString()).isEqualTo("submitted");
        assertThat(submitted.path("overallScore").decimalValue()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(submitted.path("scores")).hasSize(3);
        assertThat(submitted.path("recommendation").asString()).isEqualTo("yes");
        assertThat(submitted.path("submittedAt").asString()).isNotBlank();
        assertThat(Bell.of(jdbc, recruiter.id()).getFirst()).satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("EVALUATION_SUBMITTED");
            assertThat(bell.link()).isEqualTo("/app/candidatos/" + app.candidateId() + "?candidatura=" + app.id());
        });

        send("POST", submit, first, scorecard(1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Esta avaliação já foi submetida."));
        // Rounded half up to one decimal: (4 + 4 + 5) / 3 = 4.33 → 4.3
        String other = pendingOf(second, interviewId);
        JsonNode rounded = json(send("POST", "/api/v1/evaluations/" + other + "/submit", second, scorecard(4, 4, 5)).andExpect(status().isOk()));
        assertThat(rounded.path("overallScore").decimalValue()).isEqualByComparingTo(new BigDecimal("4.3"));
    }

    @Test
    void anInterviewerSeesTheOthersEvaluationsOnlyAfterSubmittingTheirOwn() throws Exception {
        App app = apply(openJob());
        String interviewId = doneInterview(app, first, second);
        String firstEvaluation = pendingOf(first, interviewId);
        String secondEvaluation = pendingOf(second, interviewId);
        String byApplication = "/api/v1/applications/" + app.id() + "/evaluations";
        String byCandidate = "/api/v1/candidates/" + app.candidateId() + "/evaluations";

        send("POST", "/api/v1/evaluations/" + firstEvaluation + "/submit", first, scorecard(5)).andExpect(status().isOk());

        // The second interviewer has not submitted: only their own (pending)
        JsonNode blind = json(read(byApplication, second).andExpect(status().isOk()));
        assertThat(blind).hasSize(1);
        assertThat(blind.get(0).path("id").asString()).isEqualTo(secondEvaluation);
        assertThat(json(read(byCandidate, second))).hasSize(1);
        read("/api/v1/evaluations/" + firstEvaluation, second)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Só pode ver as avaliações dos colegas depois de submeter a sua."));

        // The first one submitted: sees the others' submitted ones only (the second is still pending)
        assertThat(json(read(byApplication, first))).hasSize(1);

        send("POST", "/api/v1/evaluations/" + secondEvaluation + "/submit", second, scorecard(2)).andExpect(status().isOk());
        assertThat(json(read(byApplication, second))).hasSize(2);
        assertThat(json(read(byApplication, first))).hasSize(2);
        read("/api/v1/evaluations/" + firstEvaluation, second).andExpect(status().isOk());

        // Recruiters see everything (no reopenings yet); an unrelated interviewer and a client never
        JsonNode all = json(read(byApplication, recruiter).andExpect(status().isOk()));
        assertThat(all).hasSize(2);
        assertThat(all.get(0).path("reopenings")).isEmpty();
        read(byApplication, outsider).andExpect(status().isForbidden());
        assertThat(json(read(byCandidate, outsider).andExpect(status().isOk()))).isEmpty();
        read("/api/v1/evaluations/" + firstEvaluation, outsider).andExpect(status().isForbidden());
        TestUser client = users.createClientUser(clientId);
        read(byApplication, client).andExpect(status().isForbidden());
        read("/api/v1/evaluations/mine", client).andExpect(status().isForbidden());
    }

    @Test
    void administratorsAndRecruitersAddFreeEvaluations() throws Exception {
        App app = apply(openJob());
        String url = "/api/v1/applications/" + app.id() + "/evaluations";

        send("POST", url, first, scorecard(3)).andExpect(status().isForbidden());
        send("POST", url, recruiter, scorecard(0)).andExpect(status().isUnprocessableContent());
        send("POST", "/api/v1/applications/" + UUID.randomUUID() + "/evaluations", recruiter, scorecard(3)).andExpect(status().isNotFound());

        // Another recruiter (test administrators are deleted by other tests: they never author evaluations here)
        TestUser colleague = users.create(UserRole.RECRUITER);
        JsonNode created = json(send("POST", url, colleague, scorecard(3, 4)).andExpect(status().isCreated()));
        assertThat(created.path("status").asString()).isEqualTo("submitted");
        assertThat(created.path("interviewId").isNull()).isTrue();
        assertThat(created.path("interview").isNull()).isTrue();
        assertThat(created.path("evaluatorId").asString()).isEqualTo(colleague.id().toString());
        assertThat(created.path("overallScore").decimalValue()).isEqualByComparingTo(new BigDecimal("3.5"));
        // The job's recruiter is told (a colleague evaluated)
        assertThat(Bell.of(jdbc, recruiter.id()).getFirst().type()).isEqualTo("EVALUATION_SUBMITTED");

        int bells = Bell.of(jdbc, recruiter.id()).size();
        send("POST", url, recruiter, scorecard(5)).andExpect(status().isCreated());
        assertThat(Bell.of(jdbc, recruiter.id())).hasSize(bells);
        assertThat(json(read(url, recruiter))).hasSize(2);
    }

    @Test
    void onlyAnAdministratorReopensWithAReasonKeptInTheHistory() throws Exception {
        App app = apply(openJob());
        String interviewId = doneInterview(app, first);
        String id = pendingOf(first, interviewId);
        String reopen = "/api/v1/evaluations/" + id + "/reopen";

        send("POST", reopen, admin, Map.of("reason", "Falta avaliar a comunicação."))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Só pode reabrir uma avaliação já submetida."));
        send("POST", "/api/v1/evaluations/" + id + "/submit", first, scorecard(4)).andExpect(status().isOk());

        send("POST", reopen, recruiter, Map.of("reason", "Falta avaliar a comunicação.")).andExpect(status().isForbidden());
        send("POST", reopen, first, Map.of("reason", "Falta avaliar a comunicação.")).andExpect(status().isForbidden());
        send("POST", reopen, admin, Map.of("reason", "ok")).andExpect(status().isBadRequest());

        JsonNode reopened = json(send("POST", reopen, admin, Map.of("reason", "Falta avaliar a comunicação.")).andExpect(status().isOk()));
        assertThat(reopened.path("status").asString()).isEqualTo("pending");
        assertThat(reopened.path("submittedAt").isNull()).isTrue();
        assertThat(reopened.path("reopenings")).hasSize(1);
        assertThat(reopened.path("reopenings").get(0).path("reason").asString()).isEqualTo("Falta avaliar a comunicação.");
        assertThat(reopened.path("reopenings").get(0).path("reopenedById").asString()).isEqualTo(admin.id().toString());
        assertThat(Bell.of(jdbc, first.id()).getFirst()).satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("EVALUATION_REOPENED");
            assertThat(bell.body()).contains("Falta avaliar a comunicação.");
            assertThat(bell.link()).isEqualTo("/app/minhas-avaliacoes");
        });
        // The evaluator does not see the history; they submit again, and a second reopening is added (never overwritten)
        assertThat(json(read("/api/v1/evaluations/" + id, first)).path("reopenings")).isEmpty();
        send("POST", "/api/v1/evaluations/" + id + "/submit", first, scorecard(3, 4)).andExpect(status().isOk());
        send("POST", reopen, admin, Map.of("reason", "Rever a recomendação final.")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluation_reopenings WHERE evaluation_id = ?::uuid", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void erasureKeepsScoresAndRecommendationWithoutAnyFreeText() throws Exception {
        App app = apply(openJob());
        String interviewId = doneInterview(app, first, second);
        String submitted = pendingOf(first, interviewId);
        String pending = pendingOf(second, interviewId);
        send("POST", "/api/v1/evaluations/" + submitted + "/submit", first, scorecard(4, 5)).andExpect(status().isOk());
        send("POST", "/api/v1/evaluations/" + submitted + "/reopen", admin, Map.of("reason", "A Helena merece nova análise.")).andExpect(status().isOk());
        send("POST", "/api/v1/evaluations/" + submitted + "/submit", first, scorecard(4, 5)).andExpect(status().isOk());

        erasureService.anonymize(app.candidateId(), ErasureLog.Reason.HOLDER_REQUEST, null);

        Map<String, Object> row = jdbc.queryForMap("SELECT status, recommendation, overall_score, strengths, concerns, notes FROM evaluations WHERE id = ?::uuid",
                submitted);
        assertThat(row.get("status")).isEqualTo("SUBMITTED");
        assertThat(row.get("recommendation")).isEqualTo("YES");
        assertThat((BigDecimal) row.get("overall_score")).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(row.get("strengths")).isNull();
        assertThat(row.get("concerns")).isNull();
        assertThat(row.get("notes")).isNull();
        assertThat(jdbc.queryForList("SELECT score FROM evaluation_scores WHERE evaluation_id = ?::uuid ORDER BY position", Integer.class, submitted))
                .containsExactly(4, 5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluation_scores WHERE evaluation_id = ?::uuid AND comment IS NOT NULL", Integer.class, submitted))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluation_reopenings WHERE evaluation_id = ?::uuid AND reason IS NOT NULL", Integer.class, submitted))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evaluations WHERE id = ?::uuid", Integer.class, pending)).isZero();
    }
}
