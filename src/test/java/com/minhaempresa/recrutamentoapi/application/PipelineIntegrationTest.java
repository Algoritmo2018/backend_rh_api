package com.minhaempresa.recrutamentoapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Stage 8b: the pipeline (list, history, move, reject, restore, edit, talent pool → job, messages, interviewers). */
@IntegrationTest
class PipelineIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String APPLICATIONS = "/api/v1/applications";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    TestUser recruiter;
    TestUser interviewer;
    UUID clientId;

    record Job(UUID id, String title) {}

    /** An application stored through the public form. */
    record App(UUID id, UUID candidateId, String email) {}

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        recruiter = users.create(UserRole.RECRUITER);
        interviewer = users.create(UserRole.INTERVIEWER);
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
        return openJob(interviewer, false);
    }

    Job openJob(TestUser jobInterviewer, boolean confidential) throws Exception {
        Map<String, Object> body = JobBodies.draft(clientId, recruiter.id(), jobInterviewer.id());
        String title = "Operador de sonda " + UUID.randomUUID().toString().substring(0, 6);
        body.put("title", title);
        body.put("status", "open");
        body.put("isConfidential", confidential);
        JsonNode job = json(api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), body).andExpect(status().isCreated()));
        return new Job(UUID.fromString(job.path("id").asString()), title);
    }

    App apply(Job job, Map<String, Object> quickProfile, boolean talentPool) throws Exception {
        String email = TestUsers.uniqueEmail();
        JsonNode questions = json(mvc.perform(get("/api/v1/jobs/" + job.id()).with(users.bearer(recruiter))).andExpect(status().isOk())).path("screeningQuestions");
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", job.id().toString());
        body.put("fullName", "Pedro Afonso Teste");
        body.put("email", email);
        body.put("phone", TestUsers.uniqueLocalPhone());
        body.put("province", "Luanda");
        body.put("screeningAnswers", List.of(Map.of("questionId", questions.get(0).path("id").asString(), "answer", "sim")));
        body.put("consentJob", true);
        body.put("consentTalentPool", talentPool);
        body.put("source", "linkedin");
        if (quickProfile != null) {
            body.put("quickProfile", quickProfile);
        }
        var request = multipart("/api/v1/public/applications")
                .file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(body)));
        if (quickProfile == null) {
            request.file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", SampleFiles.pdf()));
        }
        mvc.perform(request.with(TestUsers.randomIp())).andExpect(status().isAccepted());
        Map<String, Object> row = jdbc.queryForMap("SELECT a.id, a.candidate_id FROM applications a WHERE a.job_id = ? AND a.email = ?", job.id(), email);
        return new App((UUID) row.get("id"), (UUID) row.get("candidate_id"), email);
    }

    App apply(Job job) throws Exception {
        return apply(job, null, false);
    }

    static Map<String, Object> nebosh() {
        Map<String, Object> quick = new HashMap<>();
        quick.put("headline", "Técnico HSE");
        quick.put("skills", List.of("NEBOSH"));
        quick.put("years", 5);
        quick.put("educationLevel", "technical");
        return quick;
    }

    JsonNode card(UUID id, TestUser viewer) throws Exception {
        return json(mvc.perform(get(APPLICATIONS + "/" + id).with(users.bearer(viewer))).andExpect(status().isOk()));
    }

    long version(UUID id) {
        return jdbc.queryForObject("SELECT version FROM applications WHERE id = ?", Long.class, id);
    }

    ResultActions postJson(String url, TestUser user, Object body) throws Exception {
        return mvc.perform(withJson(post(url), body).with(users.bearer(user)));
    }

    ResultActions move(UUID id, TestUser user, Map<String, Object> body) throws Exception {
        return postJson(APPLICATIONS + "/" + id + "/move", user, body);
    }

    Map<String, Object> moveBody(String toStage, long version) {
        Map<String, Object> body = new HashMap<>();
        body.put("toStage", toStage);
        body.put("version", version);
        return body;
    }

    ResultActions reject(TestUser user, List<UUID> ids, boolean notify, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", ids.stream().map(UUID::toString).toList());
        body.put("reasonId", reasonId("client").toString());
        body.put("notify", notify);
        body.put("note", note);
        return postJson(APPLICATIONS + "/reject", user, body);
    }

    UUID reasonId(String code) {
        return jdbc.queryForObject("SELECT id FROM rejection_reasons WHERE code = ?", UUID.class, code);
    }

    static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }

    static JsonNode json(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? JSON.missingNode() : JSON.readTree(content);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    UUID smsTemplate(String body) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Modelo " + UUID.randomUUID().toString().substring(0, 8));
        request.put("channel", "sms");
        request.put("category", "general");
        request.put("subject", null);
        request.put("body", body);
        return UUID.fromString(json(postJson("/api/v1/message-templates", recruiter, request).andExpect(status().isCreated())).path("id").asString());
    }

    // =====================================================================================

    @Nested
    class ListAndMove {

        @Test
        void thePipelineListsCardsAndMovesWithTheVersionAndHistory() throws Exception {
            Job job = openJob();
            App cvOnly = apply(job);
            App scored = apply(job, nebosh(), false);

            JsonNode pipeline = json(mvc.perform(get("/api/v1/jobs/" + job.id() + "/applications").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(pipeline).hasSize(2);
            JsonNode first = pipeline.get(0);
            assertThat(first.path("id").asString()).isEqualTo(scored.id().toString());
            assertThat(first.path("score").asInt()).isEqualTo(100);
            assertThat(first.path("stage").asString()).isEqualTo("new");
            assertThat(first.path("status").asString()).isEqualTo("active");
            assertThat(first.path("sourceId").asString()).isEqualTo("linkedin");
            assertThat(first.path("quickProfile").path("headline").asString()).isEqualTo("Técnico HSE");
            assertThat(first.path("screeningAnswers").get(0).path("answer").asString()).isEqualTo("sim");
            assertThat(first.path("clientComments")).isEmpty();
            assertThat(first.path("candidate").path("fullName").asString()).isEqualTo("Pedro Afonso Teste");
            assertThat(first.path("candidate").path("email").asString()).isEqualTo(scored.email());
            assertThat(first.path("candidate").path("province").asString()).isEqualTo("Luanda");
            JsonNode second = pipeline.get(1);
            assertThat(second.path("score").isNull()).isTrue();
            assertThat(second.path("cvUrl").asString()).startsWith("/api/v1/documents/").endsWith("/file");

            // Move with the version read → new stage, history with the user and the note
            long version = first.path("version").asLong();
            Map<String, Object> body = moveBody("screening", version);
            body.put("note", "Perfil forte");
            JsonNode moved = json(move(scored.id(), recruiter, body).andExpect(status().isOk()));
            assertThat(moved.path("stage").asString()).isEqualTo("screening");
            assertThat(moved.path("version").asLong()).isGreaterThan(version);

            // The same (now stale) version again → 409 and nothing changes
            move(scored.id(), recruiter, moveBody("hr_interview", version))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Os dados foram alterados por outra pessoa entretanto. Recarregue a página e tente novamente."));
            assertThat(card(scored.id(), recruiter).path("stage").asString()).isEqualTo("screening");

            // Client interview marks it shared and approved; the final stage makes it hired
            JsonNode client = json(move(scored.id(), recruiter, moveBody("client_interview", version(scored.id()))).andExpect(status().isOk()));
            assertThat(client.path("sharedWithClient").asBoolean()).isTrue();
            assertThat(client.path("clientDecision").asString()).isEqualTo("approved");
            JsonNode hired = json(move(scored.id(), recruiter, moveBody("hired", version(scored.id()))).andExpect(status().isOk()));
            assertThat(hired.path("status").asString()).isEqualTo("hired");

            JsonNode history = json(mvc.perform(get(APPLICATIONS + "/" + scored.id() + "/history").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(history).hasSize(4);
            assertThat(history.get(0).path("action").asString()).isEqualTo("moved");
            assertThat(history.get(0).path("toStage").asString()).isEqualTo("hired");
            assertThat(history.get(2).path("reason").asString()).isEqualTo("Perfil forte");
            assertThat(history.get(2).path("byUserId").asString()).isEqualTo(recruiter.id().toString());
            assertThat(history.get(2).path("byUserName").asString()).isNotBlank();
            assertThat(history.get(3).path("action").asString()).isEqualTo("applied");
            assertThat(history.get(3).path("byUserId").isNull()).isTrue();

            // Job list counts follow
            JsonNode listed = json(mvc.perform(get("/api/v1/jobs/" + job.id()).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(listed.path("stageCounts").path("hired").asLong()).isEqualTo(1);
            assertThat(listed.path("stageCounts").path("new").asLong()).isEqualTo(1);

            mvc.perform(get(APPLICATIONS + "/" + UUID.randomUUID()).with(users.bearer(recruiter))).andExpect(status().isNotFound());
            move(cvOnly.id(), recruiter, Map.of("toStage", "screening")).andExpect(status().isBadRequest());
        }

        @Test
        void theAutomaticActionIsSentAsAnAutomaticMessageOfTheUserOrNothingMoves() throws Exception {
            Job job = openJob();
            App app = apply(job);
            UUID templateId = smsTemplate("Olá {{nome}}, avançou na vaga {{vaga}} ({{empresa}}).");

            Map<String, Object> body = moveBody("screening", version(app.id()));
            body.put("runAutoAction", true);
            body.put("templateId", templateId.toString());
            move(app.id(), recruiter, body).andExpect(status().isOk());

            Map<String, Object> message = jdbc.queryForMap("SELECT automatic, sent_by, body FROM messages WHERE application_id = ? AND template_id = ?", app.id(), templateId);
            assertThat(message).containsEntry("automatic", true).containsEntry("sent_by", recruiter.id());
            String company = jdbc.queryForObject("SELECT name FROM clients WHERE id = ?", String.class, clientId);
            assertThat((String) message.get("body")).isEqualTo("Olá Pedro, avançou na vaga " + job.title() + " (" + company + ").");

            // The stage's own template (interview invitation) needs the interview date, unknown until stage 9 → 422, nothing moves
            Map<String, Object> interview = moveBody("hr_interview", version(app.id()));
            interview.put("runAutoAction", true);
            move(app.id(), recruiter, interview)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("{{data_entrevista}}")));
            assertThat(card(app.id(), recruiter).path("stage").asString()).isEqualTo("screening");
            assertThat(count("SELECT count(*) FROM stage_moves WHERE application_id = ? AND to_stage = 'HR_INTERVIEW'", app.id())).isZero();

            // Without the automatic action it moves
            move(app.id(), recruiter, moveBody("hr_interview", version(app.id()))).andExpect(status().isOk());
        }

        @Test
        void bulkMoveChecksEveryVersionAndSkipsRejectedOnes() throws Exception {
            Job job = openJob();
            App a = apply(job);
            App b = apply(job);
            App c = apply(job);
            reject(recruiter, List.of(c.id()), false, null).andExpect(status().isOk());

            // One stale version → 409, nothing moves
            Map<String, Object> stale = new HashMap<>();
            stale.put("toStage", "screening");
            stale.put("items", List.of(Map.of("id", a.id().toString(), "version", version(a.id())), Map.of("id", b.id().toString(), "version", version(b.id()) + 5)));
            postJson(APPLICATIONS + "/bulk-move", recruiter, stale)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].field").value("items[1].version"));
            assertThat(count("SELECT count(*) FROM applications WHERE job_id = ? AND stage = 'SCREENING'", job.id())).isZero();

            Map<String, Object> body = new HashMap<>();
            body.put("toStage", "screening");
            body.put("note", "Triagem em lote");
            body.put("items", List.of(Map.of("id", a.id().toString(), "version", version(a.id())), Map.of("id", b.id().toString(), "version", version(b.id())),
                    Map.of("id", c.id().toString(), "version", version(c.id()))));
            postJson(APPLICATIONS + "/bulk-move", recruiter, body).andExpect(status().isOk()).andExpect(jsonPath("$.moved").value(2));
            assertThat(count("SELECT count(*) FROM applications WHERE job_id = ? AND stage = 'SCREENING'", job.id())).isEqualTo(2);
            assertThat(count("SELECT count(*) FROM application_bulk_actions WHERE user_id = ? AND action = 'MOVE'", recruiter.id())).isEqualTo(1);
        }
    }

    // =====================================================================================

    @Nested
    class RejectRestoreAndEdit {

        @Test
        void aRejectedApplicationMustBeRestoredBeforeMovingAndGoesBackToItsStage() throws Exception {
            Job job = openJob();
            App app = apply(job);
            move(app.id(), recruiter, moveBody("screening", version(app.id()))).andExpect(status().isOk());

            reject(recruiter, List.of(app.id()), false, "Sem disponibilidade").andExpect(status().isOk()).andExpect(jsonPath("$.rejected").value(1));
            JsonNode rejected = card(app.id(), recruiter);
            assertThat(rejected.path("status").asString()).isEqualTo("rejected");
            assertThat(rejected.path("rejectionReasonId").asString()).isEqualTo(reasonId("client").toString());
            assertThat(rejected.path("rejectionNote").asString()).isEqualTo("Sem disponibilidade");
            assertThat(rejected.path("rejectedById").asString()).isEqualTo(recruiter.id().toString());
            String label = jdbc.queryForObject("SELECT label FROM rejection_reasons WHERE code = 'client'", String.class);
            assertThat(jdbc.queryForObject("SELECT reason FROM stage_moves WHERE application_id = ? AND action = 'REJECTED'", String.class, app.id()))
                    .isEqualTo(label + " – Sem disponibilidade");
            // A single rejection is not a bulk action, and rejecting it again changes nothing
            assertThat(count("SELECT count(*) FROM application_bulk_actions WHERE user_id = ?", recruiter.id())).isZero();
            reject(recruiter, List.of(app.id()), false, null).andExpect(jsonPath("$.rejected").value(0));

            move(app.id(), recruiter, moveBody("hr_interview", version(app.id())))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Reponha a candidatura antes de a mover."));

            JsonNode restored = json(postJson(APPLICATIONS + "/" + app.id() + "/restore", recruiter, Map.of()).andExpect(status().isOk()));
            assertThat(restored.path("status").asString()).isEqualTo("active");
            assertThat(restored.path("stage").asString()).isEqualTo("screening");
            assertThat(restored.path("rejectionReasonId").isNull()).isTrue();
            assertThat(restored.path("rejectedAt").isNull()).isTrue();
            assertThat(jdbc.queryForMap("SELECT to_stage, by_user_id FROM stage_moves WHERE application_id = ? AND action = 'RESTORED'", app.id()))
                    .containsEntry("to_stage", "SCREENING").containsEntry("by_user_id", recruiter.id());
            postJson(APPLICATIONS + "/" + app.id() + "/restore", recruiter, Map.of())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Esta candidatura não está rejeitada."));

            // An inactive (or unknown) reason → 422
            Map<String, Object> badReason = new HashMap<>();
            badReason.put("ids", List.of(app.id().toString()));
            badReason.put("reasonId", UUID.randomUUID().toString());
            postJson(APPLICATIONS + "/reject", recruiter, badReason)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("reasonId"));
        }

        @Test
        void bulkRejectionOfThreeWithNotifySendsThreeSingleRecipientEmails() throws Exception {
            Job job = openJob();
            List<App> apps = List.of(apply(job), apply(job), apply(job));

            reject(recruiter, apps.stream().map(App::id).toList(), true, null).andExpect(status().isOk()).andExpect(jsonPath("$.rejected").value(3));

            String subject = "A sua candidatura – " + job.title();
            for (App app : apps) {
                // "Candidatura recebida" + "Não seleção"
                Email email = mailpit.awaitEmailsTo(app.email(), 2).stream().filter(e -> e.subject().equals(subject)).findFirst().orElseThrow();
                Map<String, List<String>> headers = mailpit.headers(email.id());
                assertThat(headers.get("To")).singleElement().asString().contains(app.email());
                assertThat(headers.get("To").getFirst().split(",")).hasSize(1);
                assertThat(headers).doesNotContainKey("Cc").doesNotContainKey("Bcc");
                assertThat(jdbc.queryForMap("SELECT automatic, sent_by, template_code FROM messages WHERE application_id = ? AND template_code = 'rejection-email'", app.id()))
                        .containsEntry("automatic", true).containsEntry("sent_by", recruiter.id());
            }
            assertThat(count("SELECT count(*) FROM outbound_emails WHERE subject = ?", subject)).isEqualTo(3);
            assertThat(count("SELECT count(*) FROM application_bulk_actions WHERE user_id = ? AND action = 'REJECT' AND items = 3", recruiter.id())).isEqualTo(1);
        }

        @Test
        void ratingTagsAndSummaryAreEdited() throws Exception {
            Job job = openJob();
            App app = apply(job);
            TestUser admin = users.create(UserRole.ADMIN);
            UUID tagId = UUID.fromString(json(postJson("/api/v1/settings/tags", admin,
                    Map.of("name", "Prioridade " + UUID.randomUUID().toString().substring(0, 6), "color", "cyan")).andExpect(status().isCreated())).path("id").asString());

            Map<String, Object> body = new HashMap<>();
            body.put("rating", 4);
            body.put("tagIds", List.of(tagId.toString()));
            body.put("recruiterSummary", "  Boa experiência em sondas.  ");
            JsonNode edited = json(mvc.perform(withJson(patch(APPLICATIONS + "/" + app.id()), body).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(edited.path("rating").asInt()).isEqualTo(4);
            assertThat(edited.path("tagIds").get(0).asString()).isEqualTo(tagId.toString());
            assertThat(edited.path("recruiterSummary").asString()).isEqualTo("Boa experiência em sondas.");
            assertThat(json(mvc.perform(get("/api/v1/settings/tags/usage").with(users.bearer(admin)))).path(tagId.toString()).asLong()).isEqualTo(1);

            // Absent fields stay; an empty summary removes it
            JsonNode partial = json(mvc.perform(withJson(patch(APPLICATIONS + "/" + app.id()), Map.of("recruiterSummary", "")).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(partial.path("rating").asInt()).isEqualTo(4);
            assertThat(partial.path("recruiterSummary").isNull()).isTrue();

            mvc.perform(withJson(patch(APPLICATIONS + "/" + app.id()), Map.of("tagIds", List.of(UUID.randomUUID().toString()))).with(users.bearer(recruiter)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("tagIds"));
            mvc.perform(withJson(patch(APPLICATIONS + "/" + app.id()), Map.of("rating", 6)).with(users.bearer(recruiter)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("rating"));
        }

        @Test
        void pausedAndClosedJobsStillAllowMovingRejectingRestoringAndEditingButNotAddingCandidates() throws Exception {
            Job job = openJob();
            App app = apply(job);
            App pool = apply(openJob(), null, true);
            String token = users.accessToken(recruiter);
            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", token, Map.of("status", "paused")).andExpect(status().isOk());

            move(app.id(), recruiter, moveBody("screening", version(app.id()))).andExpect(status().isOk());
            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", List.of(pool.candidateId().toString()), "jobId", job.id().toString()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Esta vaga já não está a aceitar candidaturas."));

            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", token, Map.of("status", "closed")).andExpect(status().isOk());
            reject(recruiter, List.of(app.id()), false, null).andExpect(jsonPath("$.rejected").value(1));
            postJson(APPLICATIONS + "/" + app.id() + "/restore", recruiter, Map.of()).andExpect(status().isOk());
            move(app.id(), recruiter, moveBody("hr_interview", version(app.id()))).andExpect(status().isOk());
            mvc.perform(withJson(patch(APPLICATIONS + "/" + app.id()), Map.of("rating", 2)).with(users.bearer(recruiter))).andExpect(status().isOk());
            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", List.of(pool.candidateId().toString()), "jobId", job.id().toString()))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    // =====================================================================================

    @Nested
    class BulkLimits {

        @Test
        void atMostFiftyItemsPerActionAndTwentyBulkActionsPerDay() throws Exception {
            List<String> tooMany = new ArrayList<>();
            for (int i = 0; i < 51; i++) {
                tooMany.add(UUID.randomUUID().toString());
            }
            Map<String, Object> rejectBody = new HashMap<>();
            rejectBody.put("ids", tooMany);
            rejectBody.put("reasonId", reasonId("client").toString());
            postJson(APPLICATIONS + "/reject", recruiter, rejectBody)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("ids"))
                    .andExpect(jsonPath("$.message").value("Selecione no máximo 50 de cada vez."));
            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", tooMany, "jobId", UUID.randomUUID().toString()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("candidateIds"));

            Job job = openJob();
            App a = apply(job);
            App b = apply(job);

            // 20 bulk actions in the last 24 hours; the oldest leaves the window in one hour
            Instant now = clock.instant();
            for (int i = 0; i < 20; i++) {
                jdbc.update("INSERT INTO application_bulk_actions (id, user_id, action, items, created_at) VALUES (?, ?, 'MOVE', 2, ?)",
                        UUID.randomUUID(), recruiter.id(), Timestamp.from(now.minus(Duration.ofHours(23)).plusSeconds(i)));
            }
            Map<String, Object> bulk = new HashMap<>();
            bulk.put("toStage", "screening");
            bulk.put("items", List.of(Map.of("id", a.id().toString(), "version", version(a.id())), Map.of("id", b.id().toString(), "version", version(b.id()))));
            String retryAfter = postJson(APPLICATIONS + "/bulk-move", recruiter, bulk)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.message").value("Atingiu o limite diário de ações em lote. Tente novamente mais tarde."))
                    .andReturn().getResponse().getHeader("Retry-After");
            assertThat(Long.parseLong(retryAfter)).isBetween(3500L, 3600L);
            assertThat(count("SELECT count(*) FROM applications WHERE job_id = ? AND stage = 'SCREENING'", job.id())).isZero();
            reject(recruiter, List.of(a.id(), b.id()), false, null).andExpect(status().isTooManyRequests());

            // Single actions are not bulk actions: still allowed
            move(a.id(), recruiter, moveBody("screening", version(a.id()))).andExpect(status().isOk());
            reject(recruiter, List.of(b.id()), false, null).andExpect(status().isOk());

            // Another recruiter has their own limit; after the window it frees up
            TestUser other = users.create(UserRole.RECRUITER);
            postJson(APPLICATIONS + "/reject", other, Map.of("ids", List.of(a.id().toString(), b.id().toString()), "reasonId", reasonId("client").toString()))
                    .andExpect(status().isOk());
            clock.advance(Duration.ofHours(2));
            Map<String, Object> later = new HashMap<>();
            later.put("toStage", "hr_interview");
            later.put("items", List.of(Map.of("id", a.id().toString(), "version", version(a.id())), Map.of("id", b.id().toString(), "version", version(b.id()))));
            postJson(APPLICATIONS + "/bulk-move", recruiter, later).andExpect(status().isOk());
        }
    }

    // =====================================================================================

    @Nested
    class TalentPoolToJob {

        @Test
        void onlyTalentPoolCandidatesAreAddedInScreeningWithoutTheAutomaticActionAndDuplicatesAreCounted() throws Exception {
            App inPool = apply(openJob(), nebosh(), true);
            App notInPool = apply(openJob());
            Job target = openJob();

            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", List.of(inPool.candidateId().toString()), "jobId", target.id().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.added").value(1))
                    .andExpect(jsonPath("$.duplicates").value(0));
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM applications WHERE job_id = ? AND candidate_id = ?", target.id(), inPool.candidateId());
            assertThat(row).containsEntry("stage", "SCREENING").containsEntry("status", "ACTIVE").containsEntry("source", "TALENT_POOL")
                    .containsEntry("email", inPool.email()).containsEntry("full_name", "Pedro Afonso Teste");
            // The stored profile is evaluated: NEBOSH (from the quick profile) but no experience entries → 10 of 13
            assertThat(row.get("score")).isEqualTo(77);
            assertThat(jdbc.queryForMap("SELECT action, to_stage, by_user_id, reason FROM stage_moves WHERE application_id = ?", row.get("id")))
                    .containsEntry("action", "APPLIED").containsEntry("to_stage", "SCREENING").containsEntry("by_user_id", recruiter.id())
                    .containsEntry("reason", "Adicionado a partir do banco de talentos");
            assertThat(count("SELECT count(*) FROM messages WHERE application_id = ?", row.get("id"))).as("no automatic action (D8-7)").isZero();

            // Again → a duplicate, nothing changes
            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", List.of(inPool.candidateId().toString()), "jobId", target.id().toString()))
                    .andExpect(jsonPath("$.added").value(0))
                    .andExpect(jsonPath("$.duplicates").value(1));

            // A candidate without the talent-pool consent → 422 and nobody is added
            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", List.of(inPool.candidateId().toString(), notInPool.candidateId().toString()),
                    "jobId", target.id().toString()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("candidateIds"));
            assertThat(count("SELECT count(*) FROM applications WHERE job_id = ?", target.id())).isEqualTo(1);
            postJson("/api/v1/talent-pool/add-to-job", recruiter, Map.of("candidateIds", List.of(inPool.candidateId().toString()), "jobId", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================================

    @Nested
    class MessagesAndProfile {

        @Test
        void messagesAndThePreviewMayBeAboutAnApplication() throws Exception {
            Job job = openJob(interviewer, true);
            App app = apply(job);
            UUID templateId = smsTemplate("{{nome}}, sobre a vaga {{vaga}} na {{empresa}}.");

            // The candidate is not in the talent pool: the application gives the consent
            Map<String, Object> send = new HashMap<>();
            send.put("candidateId", app.candidateId().toString());
            send.put("applicationId", app.id().toString());
            send.put("channel", "sms");
            send.put("templateId", templateId.toString());
            JsonNode sent = json(postJson("/api/v1/messages", recruiter, send).andExpect(status().isCreated()));
            assertThat(sent.path("body").asString()).isEqualTo("Pedro, sobre a vaga " + job.title() + " na Empresa confidencial.");
            assertThat(sent.path("applicationId").asString()).isEqualTo(app.id().toString());
            assertThat(sent.path("automatic").asBoolean()).isFalse();

            mvc.perform(get("/api/v1/message-templates/" + templateId + "/preview").param("candidateId", app.candidateId().toString())
                            .param("applicationId", app.id().toString()).with(users.bearer(recruiter)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.body").value("Pedro, sobre a vaga " + job.title() + " na Empresa confidencial."));

            JsonNode inbox = json(mvc.perform(get("/api/v1/messages").param("applicationId", app.id().toString()).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(inbox.path("items")).isNotEmpty();
            inbox.path("items").forEach(item -> assertThat(item.path("jobTitle").asString()).isEqualTo(job.title()));

            // Without an application the talent-pool consent is still required; another candidate's application → 422
            send.remove("applicationId");
            postJson("/api/v1/messages", recruiter, send).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("candidateId"));
            App other = apply(job);
            send.put("applicationId", other.id().toString());
            postJson("/api/v1/messages", recruiter, send).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("applicationId"));
        }

        @Test
        void theCandidatePageListsTheApplications() throws Exception {
            Job first = openJob();
            App app = apply(first);
            reject(recruiter, List.of(app.id()), false, null).andExpect(status().isOk());

            JsonNode profile = json(mvc.perform(get("/api/v1/candidates/" + app.candidateId() + "/profile").with(users.bearer(recruiter))).andExpect(status().isOk()));
            JsonNode application = profile.path("applications").get(0);
            assertThat(application.path("id").asString()).isEqualTo(app.id().toString());
            assertThat(application.path("job").path("title").asString()).isEqualTo(first.title());
            assertThat(application.path("job").path("recruiterId").asString()).isEqualTo(recruiter.id().toString());
            assertThat(application.path("job").path("recruiterName").asString()).isNotBlank();
            assertThat(application.path("job").path("requirements")).hasSize(2);
            assertThat(application.path("job").path("testId").isNull()).isTrue();
            assertThat(application.path("stageName").asString()).isEqualTo("Nova");
            assertThat(application.path("sourceName").asString()).isEqualTo("LinkedIn");
            String label = jdbc.queryForObject("SELECT label FROM rejection_reasons WHERE code = 'client'", String.class);
            assertThat(application.path("rejectionReasonLabel").asString()).isEqualTo(label);
            assertThat(application.path("commentAuthors").isEmpty()).isTrue();
        }
    }

    // =====================================================================================

    @Nested
    class InterviewerIsolation {

        @Test
        void anInterviewerOnlyReadsTheAssignedJobsInTheRestrictedViewAndNeverActs() throws Exception {
            Job assigned = openJob();
            TestUser otherInterviewer = users.create(UserRole.INTERVIEWER);
            Job notAssigned = openJob(otherInterviewer, false);
            App mine = apply(assigned);
            // The same candidate also applied to a job of another interviewer
            App foreign = apply(notAssigned);
            jdbc.update("UPDATE applications SET candidate_id = ? WHERE id = ?", mine.candidateId(), foreign.id());

            // Assigned job: restricted cards (no email, phone nor photo)
            JsonNode pipeline = json(mvc.perform(get("/api/v1/jobs/" + assigned.id() + "/applications").with(users.bearer(interviewer))).andExpect(status().isOk()));
            assertThat(pipeline).hasSize(1);
            JsonNode candidate = pipeline.get(0).path("candidate");
            assertThat(candidate.path("fullName").asString()).isEqualTo("Pedro Afonso Teste");
            assertThat(candidate.path("email").isNull()).isTrue();
            assertThat(candidate.path("phone").isNull()).isTrue();
            assertThat(candidate.path("avatarUrl").isNull()).isTrue();
            assertThat(card(mine.id(), interviewer).path("candidate").path("email").isNull()).isTrue();
            mvc.perform(get(APPLICATIONS + "/" + mine.id() + "/history").with(users.bearer(interviewer))).andExpect(status().isOk());

            // Another job: 403, consistently for the list, the application and the history
            mvc.perform(get("/api/v1/jobs/" + notAssigned.id() + "/applications").with(users.bearer(interviewer)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Esta vaga não lhe foi atribuída."));
            mvc.perform(get(APPLICATIONS + "/" + foreign.id()).with(users.bearer(interviewer)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Esta candidatura não lhe foi atribuída."));
            mvc.perform(get(APPLICATIONS + "/" + foreign.id() + "/history").with(users.bearer(interviewer))).andExpect(status().isForbidden());

            // The candidate page lists only the application of the assigned job
            JsonNode profile = json(mvc.perform(get("/api/v1/candidates/" + mine.candidateId() + "/profile").with(users.bearer(interviewer))).andExpect(status().isOk()));
            assertThat(profile.path("restricted").asBoolean()).isTrue();
            assertThat(profile.path("applications")).hasSize(1);
            assertThat(profile.path("applications").get(0).path("id").asString()).isEqualTo(mine.id().toString());
            JsonNode staffProfile = json(mvc.perform(get("/api/v1/candidates/" + mine.candidateId() + "/profile").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(staffProfile.path("applications")).hasSize(2);

            // No actions at all
            move(mine.id(), interviewer, moveBody("screening", version(mine.id()))).andExpect(status().isForbidden());
            postJson(APPLICATIONS + "/bulk-move", interviewer, Map.of("toStage", "screening", "items", List.of(Map.of("id", mine.id().toString(), "version", version(mine.id())))))
                    .andExpect(status().isForbidden());
            reject(interviewer, List.of(mine.id()), false, null).andExpect(status().isForbidden());
            postJson(APPLICATIONS + "/" + mine.id() + "/restore", interviewer, Map.of()).andExpect(status().isForbidden());
            mvc.perform(withJson(patch(APPLICATIONS + "/" + mine.id()), Map.of("rating", 1)).with(users.bearer(interviewer))).andExpect(status().isForbidden());
            postJson("/api/v1/talent-pool/add-to-job", interviewer, Map.of("candidateIds", List.of(mine.candidateId().toString()), "jobId", assigned.id().toString()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/messages").param("applicationId", mine.id().toString()).with(users.bearer(interviewer))).andExpect(status().isForbidden());
            assertThat(card(mine.id(), recruiter).path("stage").asString()).isEqualTo("new");

            // Candidates and clients never see the pipeline
            TestUser clientUser = users.createClientUser(clientId);
            mvc.perform(get("/api/v1/jobs/" + assigned.id() + "/applications").with(users.bearer(clientUser))).andExpect(status().isForbidden());
            mvc.perform(get(APPLICATIONS + "/" + mine.id()).with(users.bearer(clientUser))).andExpect(status().isForbidden());
        }
    }
}
