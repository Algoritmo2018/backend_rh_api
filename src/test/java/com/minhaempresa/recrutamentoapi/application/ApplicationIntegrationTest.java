package com.minhaempresa.recrutamentoapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureService;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageResponse;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.Bell;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Stage 8a: applying to a job (public form and logged-in candidate). */
@IntegrationTest
class ApplicationIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PUBLIC = "/api/v1/public/applications";
    static final String MINE = "/api/v1/me/applications";
    static final String RECEIVED = "{\"status\":\"received\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    MessageService messageService;

    @Autowired
    CandidateErasureService erasureService;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    CandidateApi candidates;
    TestUser recruiter;
    TestUser interviewer;
    UUID clientId;
    RequestPostProcessor ip;

    /** An open job created through the API: its id, question ids (rotation yes/no, English level) and requirement ids. */
    record OpenJob(UUID id, String title, String rotationQuestion, String englishQuestion, List<String> requirementIds, Map<String, Object> body, long version) {}

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        candidates = new CandidateApi(mvc, users, userService);
        recruiter = users.create(UserRole.RECRUITER);
        interviewer = users.create(UserRole.INTERVIEWER);
        clientId = users.createClient();
        ip = TestUsers.randomIp();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    OpenJob openJob() throws Exception {
        Map<String, Object> body = JobBodies.draft(clientId, recruiter.id(), interviewer.id());
        String title = "Técnico de HSE " + UUID.randomUUID().toString().substring(0, 6);
        body.put("title", title);
        body.put("status", "open");
        JsonNode job = json(api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), body).andExpect(status().isCreated()));
        List<String> requirementIds = new ArrayList<>();
        job.path("requirements").forEach(r -> requirementIds.add(r.path("id").asString()));
        JsonNode questions = job.path("screeningQuestions");
        return new OpenJob(UUID.fromString(job.path("id").asString()), title, questions.get(0).path("id").asString(), questions.get(1).path("id").asString(),
                requirementIds, body, job.path("version").asLong());
    }

    Map<String, Object> application(OpenJob job, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", job.id().toString());
        body.put("fullName", "Joana Manuel Teste");
        body.put("email", email);
        body.put("phone", TestUsers.uniqueLocalPhone());
        body.put("province", "Zaire");
        body.put("screeningAnswers", new ArrayList<>(List.of(Map.of("questionId", job.rotationQuestion(), "answer", "Sim"))));
        body.put("consentJob", true);
        body.put("consentTalentPool", false);
        body.put("source", "whatsapp");
        return body;
    }

    static Map<String, Object> quickProfile(String... skills) {
        Map<String, Object> quick = new HashMap<>();
        quick.put("headline", "Técnico de segurança");
        quick.put("skills", List.of(skills));
        quick.put("years", 5);
        quick.put("educationLevel", "technical");
        quick.put("lastRole", "Supervisor HSE");
        quick.put("lastCompany", "Empresa X");
        quick.put("driverLicenses", List.of("B"));
        return quick;
    }

    ResultActions applyPublic(Map<String, Object> body, byte[] cv) throws Exception {
        return mvc.perform(multipartRequest(PUBLIC, body, cv, "o-meu-cv.pdf").with(ip));
    }

    ResultActions applyMine(TestUser candidate, Map<String, Object> body, byte[] cv) throws Exception {
        return mvc.perform(multipartRequest(MINE, body, cv, "cv.pdf").with(users.bearer(candidate)));
    }

    static MockMultipartHttpServletRequestBuilder multipartRequest(String url, Map<String, Object> body, byte[] cv, String fileName) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(url)
                .file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(body)));
        if (cv != null) {
            request.file(new MockMultipartFile("cv", fileName, "application/pdf", cv));
        }
        return request;
    }

    static JsonNode json(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? JSON.missingNode() : JSON.readTree(content);
    }

    Map<String, Object> applicationRow(OpenJob job, String email) {
        return jdbc.queryForMap("SELECT a.* FROM applications a JOIN candidates c ON c.id = a.candidate_id WHERE a.job_id = ? AND c.email = ?", job.id(), email);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** The update body of the job (ids of its requirements and questions, version). */
    Map<String, Object> updateBody(OpenJob job) {
        Map<String, Object> body = new HashMap<>(job.body());
        List<Map<String, Object>> requirements = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> originalRequirements = (List<Map<String, Object>>) job.body().get("requirements");
        for (int i = 0; i < originalRequirements.size(); i++) {
            Map<String, Object> copy = new HashMap<>(originalRequirements.get(i));
            copy.put("id", job.requirementIds().get(i));
            requirements.add(copy);
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> originalQuestions = (List<Map<String, Object>>) job.body().get("screeningQuestions");
        List<String> questionIds = List.of(job.rotationQuestion(), job.englishQuestion());
        for (int i = 0; i < originalQuestions.size(); i++) {
            Map<String, Object> copy = new HashMap<>(originalQuestions.get(i));
            copy.put("id", questionIds.get(i));
            questions.add(copy);
        }
        body.put("requirements", requirements);
        body.put("screeningQuestions", questions);
        body.put("version", job.version());
        return body;
    }

    // =====================================================================================

    @Nested
    class PublicForm {

        @Test
        void applicationWithCvCreatesCandidateApplicationConsentsHistoryAutomaticEmailAndBell() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            Map<String, Object> body = application(job, email.toUpperCase());
            body.put("consentTalentPool", true);

            applyPublic(body, SampleFiles.pdf())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("received"));

            Map<String, Object> row = applicationRow(job, email);
            assertThat(row.get("stage")).isEqualTo("NEW");
            assertThat(row.get("status")).isEqualTo("ACTIVE");
            assertThat(row.get("source")).isEqualTo("WHATSAPP");
            assertThat(row.get("full_name")).isEqualTo("Joana Manuel Teste");
            assertThat(row.get("email")).isEqualTo(email);
            assertThat(row.get("province")).isEqualTo("ZAIRE");
            assertThat(row.get("eliminated")).isEqualTo(false);
            // CV only, no session: nothing structured to evaluate → no score (never a misleading 0)
            assertThat(row.get("score")).isNull();
            UUID applicationId = (UUID) row.get("id");
            UUID candidateId = (UUID) row.get("candidate_id");

            // The CV is a document of the new candidate and its current CV (D8-2)
            UUID cvId = (UUID) row.get("cv_document_id");
            assertThat(cvId).isNotNull();
            assertThat(jdbc.queryForObject("SELECT cv_document_id FROM candidates WHERE id = ?", UUID.class, candidateId)).isEqualTo(cvId);
            assertThat(jdbc.queryForObject("SELECT file_name FROM documents WHERE id = ?", String.class, cvId)).isEqualTo("o-meu-cv.pdf");

            // Answers (normalized), requirement matches, history
            assertThat(jdbc.queryForObject("SELECT answer FROM application_answers WHERE application_id = ?", String.class, applicationId)).isEqualTo("sim");
            assertThat(count("SELECT count(*) FROM application_requirement_matches WHERE application_id = ?", applicationId)).isZero();
            assertThat(jdbc.queryForMap("SELECT action, to_stage, by_user_id FROM stage_moves WHERE application_id = ?", applicationId))
                    .containsEntry("action", "APPLIED").containsEntry("to_stage", "NEW").containsEntry("by_user_id", null);

            // Consents: this job (text version in force) and the talent pool
            assertThat(count("SELECT count(*) FROM consents WHERE candidate_id = ? AND type = 'JOB_APPLICATION' AND job_id = ? AND text_version IS NOT NULL", candidateId, job.id())).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM consents WHERE candidate_id = ? AND type = 'TALENT_POOL' AND revoked_at IS NULL", candidateId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT in_talent_pool FROM candidates WHERE id = ?", Boolean.class, candidateId)).isTrue();

            // Automatic action of "Nova": the email "Candidatura recebida", automatic, linked to the application, no sender
            Email received = mailpit.awaitEmailTo(email);
            assertThat(received.subject()).isEqualTo("Recebemos a sua candidatura – " + job.title());
            assertThat(received.text()).contains("Olá Joana").contains(job.title());
            assertThat(jdbc.queryForMap("SELECT automatic, sent_by, template_code FROM messages WHERE application_id = ?", applicationId))
                    .containsEntry("automatic", true).containsEntry("sent_by", null).containsEntry("template_code", "app-received-email");

            // The recruiter's bell
            assertThat(Bell.of(jdbc, recruiter.id())).singleElement().satisfies(bell -> {
                assertThat(bell.type()).isEqualTo("APPLICATION_RECEIVED");
                assertThat(bell.body()).isEqualTo("Joana Manuel Teste candidatou-se à vaga " + job.title() + ".");
                assertThat(bell.link()).isEqualTo("/app/vagas/" + job.id());
            });
        }

        @Test
        void quickProfileFillsTheNewProfileAndCountsInTheScore() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            Map<String, Object> body = application(job, email);
            body.put("quickProfile", quickProfile("NEBOSH", "Primeiros socorros"));

            applyPublic(body, null).andExpect(status().isAccepted());

            Map<String, Object> row = applicationRow(job, email);
            // NEBOSH (mandatory) and 5 ≥ 3 years: everything met
            assertThat(row.get("score")).isEqualTo(100);
            assertThat(row.get("quick_headline")).isEqualTo("Técnico de segurança");
            assertThat(row.get("quick_years")).isEqualTo(5);
            assertThat(row.get("cv_document_id")).isNull();
            Map<String, Object> candidate = jdbc.queryForMap("SELECT headline, array_to_string(skills, ',') AS skills, array_to_string(driver_licenses, ',') AS licenses FROM candidates WHERE email = ?", email);
            assertThat(candidate).containsEntry("headline", "Técnico de segurança").containsEntry("skills", "NEBOSH,Primeiros socorros").containsEntry("licenses", "B");
        }

        @Test
        void aCvOrTheQuickProfileIsRequiredAndTheCvIsChecked() throws Exception {
            OpenJob job = openJob();
            applyPublic(application(job, TestUsers.uniqueEmail()), null)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("cv"))
                    .andExpect(jsonPath("$.errors[0].message").value("Envie o seu CV ou preencha o perfil rápido."));
            applyPublic(application(job, TestUsers.uniqueEmail()), SampleFiles.legacyDoc())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("cv"));
            applyPublic(application(job, TestUsers.uniqueEmail()), SampleFiles.pdf(5 * 1024 * 1024 + 1))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("cv"));
            assertThat(count("SELECT count(*) FROM applications WHERE job_id = ?", job.id())).isZero();
        }

        @Test
        void consentAndApplicantDataAreValidated() throws Exception {
            OpenJob job = openJob();
            Map<String, Object> body = application(job, "nao-e-email");
            body.put("consentJob", false);
            body.put("phone", "123");
            applyPublic(body, SampleFiles.pdf())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'consentJob')].message").value("Tem de autorizar o tratamento dos seus dados para se candidatar."))
                    .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                    .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists());
        }

        @Test
        void answersAreCheckedAndAWrongEliminatoryAnswerEliminatesSilently() throws Exception {
            OpenJob job = openJob();

            Map<String, Object> missing = application(job, TestUsers.uniqueEmail());
            missing.put("screeningAnswers", List.of());
            applyPublic(missing, SampleFiles.pdf())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("screeningAnswers." + job.rotationQuestion()))
                    .andExpect(jsonPath("$.errors[0].message").value("Responda a esta pergunta."));

            Map<String, Object> invalid = application(job, TestUsers.uniqueEmail());
            invalid.put("screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim"), Map.of("questionId", job.englishQuestion(), "answer", "Fluente")));
            applyPublic(invalid, SampleFiles.pdf())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("screeningAnswers." + job.englishQuestion()));

            Map<String, Object> unknown = application(job, TestUsers.uniqueEmail());
            unknown.put("screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim"), Map.of("questionId", UUID.randomUUID().toString(), "answer", "x")));
            applyPublic(unknown, SampleFiles.pdf()).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("screeningAnswers"));

            String eliminatedEmail = TestUsers.uniqueEmail();
            Map<String, Object> eliminated = application(job, eliminatedEmail);
            eliminated.put("screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "Não"), Map.of("questionId", job.englishQuestion(), "answer", "Avançado")));
            applyPublic(eliminated, SampleFiles.pdf()).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("received"));
            assertThat(applicationRow(job, eliminatedEmail).get("eliminated")).isEqualTo(true);
        }

        @Test
        void aSecondApplicationOfTheSameEmailGetsTheSameAnswerStoresNothingAndTellsTheEmail() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            UUID candidateId = (UUID) applicationRow(job, email).get("candidate_id");
            int documents = count("SELECT count(*) FROM documents WHERE candidate_id = ?", candidateId);
            int consents = count("SELECT count(*) FROM consents WHERE candidate_id = ?", candidateId);

            Map<String, Object> again = application(job, email);
            again.put("fullName", "Outra Pessoa Qualquer");
            again.put("consentTalentPool", true);
            applyPublic(again, SampleFiles.pdf())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("received"));

            assertThat(count("SELECT count(*) FROM applications WHERE job_id = ? AND candidate_id = ?", job.id(), candidateId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM documents WHERE candidate_id = ?", candidateId)).isEqualTo(documents);
            assertThat(count("SELECT count(*) FROM consents WHERE candidate_id = ?", candidateId)).isEqualTo(consents);
            Email notice = await().atMost(Duration.ofSeconds(15)).until(
                    () -> mailpit.awaitEmailsTo(email, 1).stream().filter(e -> e.subject().equals("Já se candidatou a esta vaga")).findFirst().orElse(null),
                    java.util.Objects::nonNull);
            assertThat(notice.text()).contains(job.title()).contains("Olá,").doesNotContain("Outra Pessoa");
        }

        @Test
        void theAlreadyAppliedEmailGoesAtMostOncePerDayPerEmailAndJob() throws Exception {
            OpenJob job = openJob();
            OpenJob otherJob = openJob();
            String email = TestUsers.uniqueEmail();
            applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            applyPublic(application(otherJob, email), SampleFiles.pdf()).andExpect(status().isAccepted());

            // Three duplicates for the first job and one for the other: one notice each
            for (int i = 0; i < 3; i++) {
                applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("received"));
            }
            applyPublic(application(otherJob, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            await().atMost(Duration.ofSeconds(15)).until(() -> duplicateNotices(email) >= 2);

            // A day later the next duplicate is told again (once)
            clock.advance(Duration.ofDays(1).plusMinutes(1));
            applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            await().atMost(Duration.ofSeconds(15)).until(() -> duplicateNotices(email) >= 3);

            // Everything queued for the address has left: the outbox holds nothing more for it
            await().atMost(Duration.ofSeconds(15)).until(() -> count("SELECT count(*) FROM outbound_emails WHERE recipient = ? AND status = 'PENDING'", email) == 0);
            assertThat(duplicateNotices(email)).isEqualTo(3);
            assertThat(count("SELECT count(*) FROM outbound_emails WHERE recipient = ? AND subject = 'Já se candidatou a esta vaga'", email)).isEqualTo(3);
            assertThat(count("SELECT count(*) FROM application_duplicate_notices")).isPositive();
        }

        @Test
        void withoutStructuredDataTheScoreIsNullAndWithDataMeetingNothingItIsZero() throws Exception {
            OpenJob job = openJob();

            // Anonymous, CV only: nothing to evaluate → null and no matches
            String cvOnly = TestUsers.uniqueEmail();
            applyPublic(application(job, cvOnly), SampleFiles.pdf()).andExpect(status().isAccepted());
            Map<String, Object> noData = applicationRow(job, cvOnly);
            assertThat(noData.get("score")).isNull();
            assertThat(count("SELECT count(*) FROM application_requirement_matches WHERE application_id = ?", noData.get("id"))).isZero();

            // Quick profile meeting no requirement (no NEBOSH, 0 years) → evaluated: 0 with every match not met
            String noMatch = TestUsers.uniqueEmail();
            Map<String, Object> body = application(job, noMatch);
            Map<String, Object> quick = quickProfile("Soldadura");
            quick.put("years", 0);
            body.put("quickProfile", quick);
            applyPublic(body, null).andExpect(status().isAccepted());
            Map<String, Object> evaluated = applicationRow(job, noMatch);
            assertThat(evaluated.get("score")).isEqualTo(0);
            assertThat(count("SELECT count(*) FROM application_requirement_matches WHERE application_id = ? AND NOT met", evaluated.get("id"))).isEqualTo(2);

            // Pipeline: score null in JSON and at the end of the list (after the 0)
            JsonNode pipeline = json(mvc.perform(get("/api/v1/jobs/" + job.id() + "/applications").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(pipeline).hasSize(2);
            assertThat(pipeline.get(0).path("score").asInt()).isZero();
            assertThat(pipeline.get(0).path("matches")).hasSize(2);
            assertThat(pipeline.get(1).path("score").isNull()).isTrue();
            assertThat(pipeline.get(1).path("matches")).isEmpty();
        }

        @Test
        void anAnonymousApplicationIsScoredOnlyWithWhatItSent() throws Exception {
            // The email belongs to a candidate whose stored profile meets the mandatory NEBOSH requirement
            TestUser owner = candidates.verifiedCandidate();
            candidates.updateProfile(owner, Map.of("province", "Zaire", "skills", List.of("NEBOSH")));
            OpenJob job = openJob();

            applyPublic(application(job, owner.email()), SampleFiles.pdf()).andExpect(status().isAccepted());
            assertThat(applicationRow(job, owner.email()).get("score")).as("CV only: the stored profile does not count").isNull();

            OpenJob second = openJob();
            Map<String, Object> withQuick = application(second, owner.email());
            withQuick.put("quickProfile", quickProfile("Soldadura"));
            applyPublic(withQuick, null).andExpect(status().isAccepted());
            // Only the 5 years sent meet the experience requirement (3 of 13)
            assertThat(applicationRow(second, owner.email()).get("score")).isEqualTo(23);

            // The same person logged in: the stored profile counts (NEBOSH, 10 of 13)
            OpenJob third = openJob();
            JsonNode created = json(applyMine(owner, Map.of("jobId", third.id().toString(), "consentJob", true, "consentTalentPool", false,
                    "screeningAnswers", List.of(Map.of("questionId", third.rotationQuestion(), "answer", "sim"))), null).andExpect(status().isCreated()));
            assertThat(jdbc.queryForObject("SELECT score FROM applications WHERE id = ?::uuid", Integer.class, created.path("applicationId").asString())).isEqualTo(77);
        }

        private int duplicateNotices(String email) {
            return (int) mailpit.awaitEmailsTo(email, 0).stream().filter(e -> e.subject().equals("Já se candidatou a esta vaga")).count();
        }

        @Test
        void anExistingCandidateIsLinkedAndItsProfileNeverChanges() throws Exception {
            TestUser account = candidates.verifiedCandidate();
            String profileName = jdbc.queryForObject("SELECT full_name FROM candidates WHERE user_id = ?", String.class, account.id());
            OpenJob job = openJob();
            Map<String, Object> body = application(job, account.email());
            body.put("fullName", "Nome Enviado Diferente");
            body.put("quickProfile", quickProfile("Soldadura"));

            applyPublic(body, SampleFiles.pdf()).andExpect(status().isAccepted());

            Map<String, Object> row = applicationRow(job, account.email());
            assertThat(row.get("full_name")).isEqualTo("Nome Enviado Diferente");
            Map<String, Object> profile = jdbc.queryForMap("SELECT id, full_name, cv_document_id, array_to_string(skills, ',') AS skills FROM candidates WHERE user_id = ?", account.id());
            assertThat(profile.get("id")).isEqualTo(row.get("candidate_id"));
            assertThat(profile.get("full_name")).isEqualTo(profileName);
            assertThat(profile.get("skills")).isEqualTo("");
            assertThat(profile.get("cv_document_id")).as("the application CV does not become the profile CV").isNull();
            assertThat(row.get("cv_document_id")).isNotNull();
        }

        @Test
        void honeypotAnswersTheSameAndStoresNothing() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            Map<String, Object> body = application(job, email);
            body.put("website", "http://spam.example");

            applyPublic(body, SampleFiles.pdf()).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("received"));

            assertThat(count("SELECT count(*) FROM candidates WHERE email = ?", email)).isZero();
        }

        @Test
        void aJobThatIsNotVisibleAnswers404() throws Exception {
            OpenJob job = openJob();
            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", users.accessToken(recruiter), Map.of("status", "paused")).andExpect(status().isOk());
            applyPublic(application(job, TestUsers.uniqueEmail()), SampleFiles.pdf())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Vaga não encontrada."));

            OpenJob expiring = openJob();
            clock.advance(Duration.ofDays(32));
            applyPublic(application(expiring, TestUsers.uniqueEmail()), SampleFiles.pdf()).andExpect(status().isNotFound());

            Map<String, Object> unknown = application(expiring, TestUsers.uniqueEmail());
            unknown.put("jobId", UUID.randomUUID().toString());
            applyPublic(unknown, SampleFiles.pdf()).andExpect(status().isNotFound());
        }

        @Test
        void perEmailLimitAlsoCountsDuplicates() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            for (int i = 0; i < 10; i++) {
                applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            }
            applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isTooManyRequests());
        }

        @Test
        void twoFirstApplicationsOfANewEmailAtTheSameTimeCreateOneCandidate() throws Exception {
            OpenJob first = openJob();
            OpenJob second = openJob();
            String email = TestUsers.uniqueEmail();

            List<MockHttpServletResponse> responses = concurrently(List.of(
                    () -> applyPublic(application(first, email), SampleFiles.pdf()).andReturn().getResponse(),
                    () -> applyPublic(application(second, email), SampleFiles.pdf()).andReturn().getResponse()));

            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(202);
            assertThat(count("SELECT count(*) FROM candidates WHERE email = ?", email)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM applications a JOIN candidates c ON c.id = a.candidate_id WHERE c.email = ?", email)).isEqualTo(2);
        }

        @Test
        void twoApplicationsOfTheSameEmailToTheSameJobAtTheSameTimeStoreOne() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();

            List<MockHttpServletResponse> responses = concurrently(List.of(
                    () -> applyPublic(application(job, email), SampleFiles.pdf()).andReturn().getResponse(),
                    () -> applyPublic(application(job, email), SampleFiles.pdf()).andReturn().getResponse()));

            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(202);
            assertThat(count("SELECT count(*) FROM applications a JOIN candidates c ON c.id = a.candidate_id WHERE c.email = ? AND a.job_id = ?", email, job.id())).isEqualTo(1);
        }
    }

    // =====================================================================================

    @Nested
    class LoggedInCandidate {

        @Test
        void appliesWithTheOwnProfileAndADuplicateIs409() throws Exception {
            TestUser me = candidates.verifiedCandidate();
            candidates.updateProfile(me, Map.of("province", "Luanda", "skills", List.of("NEBOSH")));
            OpenJob job = openJob();
            Map<String, Object> body = new HashMap<>(Map.of("jobId", job.id().toString(), "consentJob", true, "consentTalentPool", false,
                    "screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim"))));

            JsonNode created = json(applyMine(me, body, null).andExpect(status().isCreated()));

            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM applications WHERE id = ?::uuid", created.path("applicationId").asString());
            Map<String, Object> profile = jdbc.queryForMap("SELECT id, full_name, email, phone FROM candidates WHERE user_id = ?", me.id());
            assertThat(row.get("candidate_id")).isEqualTo(profile.get("id"));
            assertThat(row.get("full_name")).isEqualTo(profile.get("full_name"));
            assertThat(row.get("email")).isEqualTo(me.email());
            assertThat(row.get("phone")).isEqualTo(profile.get("phone"));
            assertThat(row.get("province")).isEqualTo("LUANDA");
            assertThat(row.get("source")).isEqualTo("PORTAL");
            // NEBOSH from the profile (weight 5×2 = 10 of 13)
            assertThat(row.get("score")).isEqualTo(77);

            applyMine(me, body, SampleFiles.pdf())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Já existe uma candidatura sua a esta vaga."));
        }

        @Test
        void aCvSentWithTheApplicationDoesNotReplaceTheProfileCv() throws Exception {
            TestUser me = candidates.verifiedCandidate();
            OpenJob job = openJob();
            Map<String, Object> body = Map.of("jobId", job.id().toString(), "consentJob", true, "consentTalentPool", false,
                    "screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim")));

            JsonNode created = json(applyMine(me, body, SampleFiles.pdf()).andExpect(status().isCreated()));

            assertThat(jdbc.queryForObject("SELECT cv_document_id FROM applications WHERE id = ?::uuid", UUID.class, created.path("applicationId").asString())).isNotNull();
            assertThat(jdbc.queryForObject("SELECT cv_document_id FROM candidates WHERE user_id = ?", UUID.class, me.id())).isNull();
        }

        @Test
        void pausedOrClosedJobsAre422AndDraftsAre404() throws Exception {
            TestUser me = candidates.verifiedCandidate();
            OpenJob job = openJob();
            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", users.accessToken(recruiter), Map.of("status", "paused")).andExpect(status().isOk());
            Map<String, Object> body = Map.of("jobId", job.id().toString(), "consentJob", true, "consentTalentPool", false,
                    "screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim")));
            applyMine(me, body, null)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Esta vaga já não está a aceitar candidaturas."));

            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", users.accessToken(recruiter), Map.of("status", "closed")).andExpect(status().isOk());
            applyMine(me, body, null).andExpect(status().isUnprocessableContent());

            Map<String, Object> draft = JobBodies.draft(clientId, recruiter.id(), interviewer.id());
            String draftId = json(api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), draft).andExpect(status().isCreated())).path("id").asString();
            applyMine(me, Map.of("jobId", draftId, "consentJob", true, "consentTalentPool", false), null).andExpect(status().isNotFound());
        }

        @Test
        void anUnconfirmedAccountIs409AndStaffIs403() throws Exception {
            OpenJob job = openJob();
            Map<String, Object> body = Map.of("jobId", job.id().toString(), "consentJob", true, "consentTalentPool", false,
                    "screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim")));
            applyMine(users.create(UserRole.CANDIDATE), body, null).andExpect(status().isConflict());
            applyMine(recruiter, body, null).andExpect(status().isForbidden());
            mvc.perform(multipartRequest(MINE, body, null, "cv.pdf")).andExpect(status().isUnauthorized());
        }
    }

    // =====================================================================================

    @Nested
    class JobRulesAndContributors {

        @Test
        void questionsAndRequirementsLockOnceTheJobHasApplications() throws Exception {
            OpenJob job = openJob();
            String token = users.accessToken(recruiter);
            // Without applications anything may change
            Map<String, Object> free = updateBody(job);
            setRequirement(free, 0, "weight", 4);
            JsonNode saved = json(api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, free).andExpect(status().isOk()));
            setRequirement(free, 0, "weight", 5);
            free.put("version", saved.path("version").asLong());
            saved = json(api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, free).andExpect(status().isOk()));

            applyPublic(application(job, TestUsers.uniqueEmail()), SampleFiles.pdf()).andExpect(status().isAccepted());
            long version = saved.path("version").asLong();

            Map<String, Object> type = updateBody(job);
            type.put("version", version);
            setQuestion(type, 1, "type", "multiple_choice");
            api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, type)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("screeningQuestions[1].type"));

            Map<String, Object> deleted = updateBody(job);
            deleted.put("version", version);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) deleted.get("screeningQuestions");
            questions.remove(1);
            api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, deleted)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("screeningQuestions"));

            Map<String, Object> expected = updateBody(job);
            expected.put("version", version);
            setQuestion(expected, 0, "expectedAnswer", "nao");
            api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, expected).andExpect(status().isUnprocessableContent());

            Map<String, Object> weight = updateBody(job);
            weight.put("version", version);
            setRequirement(weight, 1, "minValue", 5);
            api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, weight)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("requirements[1].minValue"));

            // The text may still be corrected, and new questions added
            Map<String, Object> text = updateBody(job);
            text.put("version", version);
            setQuestion(text, 0, "question", "Tem disponibilidade para o regime de rotação 28/28?");
            setRequirement(text, 0, "description", "Certificado NEBOSH IGC");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> withNew = (List<Map<String, Object>>) text.get("screeningQuestions");
            withNew.add(JobBodies.question("q-new-1", "Tem carta de condução?", "yes_no", List.of(), false, false, null));
            api.sendJson("PUT", "/api/v1/jobs/" + job.id(), token, text).andExpect(status().isOk());
        }

        @Test
        void jobListCountsTalentSearchNumbersAndInterviewerAccessFollowTheApplications() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            String skill = "Skill" + UUID.randomUUID().toString().substring(0, 8);
            Map<String, Object> body = application(job, email);
            body.put("consentTalentPool", true);
            body.put("quickProfile", quickProfile(skill));
            applyPublic(body, null).andExpect(status().isAccepted());
            UUID candidateId = (UUID) applicationRow(job, email).get("candidate_id");

            JsonNode listed = json(mvc.perform(get("/api/v1/jobs/" + job.id()).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(listed.path("stageCounts").path("new").asLong()).isEqualTo(1);
            assertThat(listed.path("totalCandidates").asLong()).isEqualTo(1);
            assertThat(listed.path("activeCandidates").asLong()).isEqualTo(1);
            assertThat(listed.path("rejectedCount").asLong()).isZero();

            JsonNode search = json(mvc.perform(get("/api/v1/candidates/search").param("skills", skill).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(search.path("items").get(0).path("applicationsCount").asLong()).isEqualTo(1);
            assertThat(search.path("items").get(0).path("activeApplications").asLong()).isEqualTo(1);

            // The job's interviewer sees the candidate (restricted); another interviewer does not
            mvc.perform(get("/api/v1/candidates/" + candidateId + "/profile").with(users.bearer(interviewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.restricted").value(true));
            mvc.perform(get("/api/v1/candidates/" + candidateId + "/profile").with(users.bearer(users.create(UserRole.INTERVIEWER))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void erasureKeepsTheApplicationWithoutPersonalData() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            Map<String, Object> body = application(job, email);
            body.put("quickProfile", quickProfile("NEBOSH"));
            applyPublic(body, SampleFiles.pdf()).andExpect(status().isAccepted());
            Map<String, Object> before = applicationRow(job, email);
            UUID applicationId = (UUID) before.get("id");

            erasureService.anonymize((UUID) before.get("candidate_id"), ErasureLog.Reason.HOLDER_REQUEST, null);

            Map<String, Object> after = jdbc.queryForMap("SELECT * FROM applications WHERE id = ?", applicationId);
            assertThat(after.get("full_name")).isEqualTo("Candidato removido");
            assertThat(after.get("email")).isEqualTo("");
            assertThat(after.get("phone")).isEqualTo("");
            assertThat(after.get("province")).isNull();
            assertThat(after.get("cv_document_id")).isNull();
            assertThat(after.get("quick_headline")).isNull();
            assertThat(after.get("status")).isEqualTo("WITHDRAWN");
            assertThat(after.get("score")).isEqualTo(before.get("score"));
            assertThat(count("SELECT count(*) FROM application_answers WHERE application_id = ?", applicationId)).isZero();
            assertThat(count("SELECT count(*) FROM application_requirement_matches WHERE application_id = ?", applicationId)).isZero();
            assertThat(count("SELECT count(*) FROM stage_moves WHERE application_id = ? AND action = 'WITHDRAWN'", applicationId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM messages WHERE application_id = ?", applicationId)).isZero();
        }

        private void setRequirement(Map<String, Object> body, int index, String field, Object value) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("requirements");
            items.get(index).put(field, value);
        }

        private void setQuestion(Map<String, Object> body, int index, String field, Object value) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("screeningQuestions");
            items.get(index).put(field, value);
        }
    }

    // =====================================================================================

    @Nested
    class MessagesOfApplications {

        @Test
        void automaticSendsRecordTheUserDoNotCountInTheLimitsAndCheckTheApplication() throws Exception {
            OpenJob job = openJob();
            String email = TestUsers.uniqueEmail();
            applyPublic(application(job, email), SampleFiles.pdf()).andExpect(status().isAccepted());
            Map<String, Object> row = applicationRow(job, email);
            UUID applicationId = (UUID) row.get("id");
            UUID candidateId = (UUID) row.get("candidate_id");
            UUID templateId = template("Sobre a vaga {{vaga}} na {{empresa}}, {{nome}}.");

            // The recruiter is at the hourly limit of manual messages
            Instant now = clock.instant();
            for (int i = 0; i < 100; i++) {
                jdbc.update("INSERT INTO messages (id, candidate_id, channel, direction, body, status, sent_by, created_at) VALUES (?, ?, 'SMS', 'OUTBOUND', 'x', 'SENT', ?, ?)",
                        UUID.randomUUID(), candidateId, recruiter.id(), Timestamp.from(now.minusSeconds(60 + i)));
            }

            MessageResponse sent = messageService.sendFromTemplate(templateId, candidateId, applicationId, null, Map.of(), recruiter.id(), true);

            assertThat(sent.sentById()).isEqualTo(recruiter.id());
            assertThat(sent.automatic()).isTrue();
            assertThat(sent.applicationId()).isEqualTo(applicationId);
            String company = jdbc.queryForObject("SELECT name FROM clients WHERE id = ?", String.class, clientId);
            assertThat(sent.body()).isEqualTo("Sobre a vaga " + job.title() + " na " + company + ", Joana.");

            // Another candidate's application → 422
            TestUser other = candidates.verifiedCandidate();
            UUID otherCandidate = jdbc.queryForObject("SELECT id FROM candidates WHERE user_id = ?", UUID.class, other.id());
            assertThatThrownBy(() -> messageService.sendFromTemplate(templateId, otherCandidate, applicationId, null, Map.of(), recruiter.id(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("message.application.invalid");
        }

        private UUID template(String body) throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("name", "Modelo " + UUID.randomUUID().toString().substring(0, 8));
            request.put("channel", "sms");
            request.put("category", "general");
            request.put("subject", null);
            request.put("body", body);
            String response = mvc.perform(post("/api/v1/message-templates").with(users.bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(request))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            return UUID.fromString(JSON.readTree(response).path("id").asString());
        }
    }

    // =====================================================================================

    static List<MockHttpServletResponse> concurrently(List<Callable<MockHttpServletResponse>> calls) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (Callable<MockHttpServletResponse> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
        }
        return responses;
    }
}
