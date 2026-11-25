package com.minhaempresa.recrutamentoapi.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Stage 9a: interviews of the internal panel (schedule, change, cancel, conflicts, notices with .ics, access). */
@IntegrationTest
class InterviewIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String INTERVIEWS = "/api/v1/interviews";
    static final ZoneId LUANDA = ZoneId.of("Africa/Luanda");
    static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("pt-PT"));
    static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

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

    @Autowired
    CandidateErasureService erasureService;

    AuthApi api;
    TestUser recruiter;
    TestUser jobInterviewer;
    TestUser interviewer;
    UUID clientId;
    TestUser clientUser;

    record Job(UUID id, String title, String question) {}

    record App(UUID id, UUID candidateId, String email) {}

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        recruiter = users.create(UserRole.RECRUITER);
        jobInterviewer = users.create(UserRole.INTERVIEWER);
        interviewer = users.create(UserRole.INTERVIEWER);
        clientId = users.createClient();
        clientUser = users.createClientUser(clientId);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    Job openJob() throws Exception {
        Map<String, Object> body = JobBodies.draft(clientId, recruiter.id(), jobInterviewer.id());
        String title = "Mecânico " + UUID.randomUUID().toString().substring(0, 6);
        body.put("title", title);
        body.put("status", "open");
        JsonNode job = json(api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), body).andExpect(status().isCreated()));
        return new Job(UUID.fromString(job.path("id").asString()), title, job.path("screeningQuestions").get(0).path("id").asString());
    }

    App apply(Job job) throws Exception {
        String email = TestUsers.uniqueEmail();
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", job.id().toString());
        body.put("fullName", "Tomás Bento Teste");
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
        return new App((UUID) row.get("id"), (UUID) row.get("candidate_id"), email);
    }

    /** A future time on the minute, {@code days} ahead. */
    Instant future(int days, int hourUtc) {
        return clock.instant().plus(Duration.ofDays(days)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(hourUtc));
    }

    Map<String, Object> fixed(App app, Instant startsAt, TestUser... interviewers) {
        Map<String, Object> body = new HashMap<>();
        body.put("applicationId", app.id().toString());
        body.put("type", "hr");
        body.put("mode", "onsite");
        body.put("location", "Sede FEH OIRAM, Talatona");
        body.put("startsAt", startsAt.toString());
        body.put("durationMinutes", 45);
        body.put("interviewerIds", java.util.Arrays.stream(interviewers).map(u -> u.id().toString()).toList());
        body.put("notifyCandidate", true);
        return body;
    }

    ResultActions create(Map<String, Object> body) throws Exception {
        return api.sendJson("POST", INTERVIEWS, users.accessToken(recruiter), body);
    }

    JsonNode created(Map<String, Object> body) throws Exception {
        return json(create(body).andExpect(status().isCreated()));
    }

    ResultActions patch(String path, Map<String, Object> body) throws Exception {
        return api.sendJson("PATCH", INTERVIEWS + path, users.accessToken(recruiter), body);
    }

    static JsonNode json(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? JSON.missingNode() : JSON.readTree(content);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** The calendar attached to the newest outbox email to {@code recipient} whose subject starts with {@code subject}. */
    String ics(String recipient, String subject) {
        return jdbc.queryForObject("SELECT attachment_content FROM outbound_emails WHERE recipient = ? AND subject LIKE ? ORDER BY created_at DESC LIMIT 1",
                String.class, recipient, subject + "%");
    }

    Email awaitSubject(String recipient, String subject) {
        await().atMost(Duration.ofSeconds(15)).until(() -> mailpit.awaitEmailsTo(recipient, 0).stream().anyMatch(e -> e.subject().startsWith(subject)));
        return mailpit.awaitEmailsTo(recipient, 0).stream().filter(e -> e.subject().startsWith(subject)).findFirst().orElseThrow();
    }

    static String luanda(Instant time) {
        return LONG.format(time.atZone(LUANDA)) + " (hora de Luanda)";
    }

    // =====================================================================================

    @Nested
    class Scheduling {

        @Test
        void aFixedInterviewInvitesTheCandidateWithTheCalendarInLuandaTimeAndTellsTheInterviewers() throws Exception {
            Job job = openJob();
            App app = apply(job);
            Instant startsAt = future(3, 9);

            JsonNode interview = created(fixed(app, startsAt, interviewer));
            String id = interview.path("id").asString();
            assertThat(interview.path("status").asString()).isEqualTo("scheduled");
            assertThat(Instant.parse(interview.path("startsAt").asString())).isEqualTo(startsAt);
            assertThat(interview.path("candidateName").asString()).isEqualTo("Tomás Bento Teste");
            assertThat(interview.path("jobTitle").asString()).isEqualTo(job.title());
            assertThat(interview.path("interviewerNames")).hasSize(1);
            assertThat(interview.path("createdById").asString()).isEqualTo(recruiter.id().toString());
            assertThat(interview.path("version").asLong()).isZero();

            // Candidate: the invitation (Luanda time, UTC+1) with the .ics, as an automatic message of the recruiter
            Email email = awaitSubject(app.email(), "Convite para entrevista – " + job.title());
            assertThat(email.text()).contains(luanda(startsAt)).contains("Sede FEH OIRAM, Talatona");
            String calendar = ics(app.email(), "Convite para entrevista");
            assertThat(calendar).contains("METHOD:REQUEST").contains("SEQUENCE:0").contains("UID:interview-" + id + "@")
                    .contains("DTSTART:" + ICS.format(startsAt)).contains("DTEND:" + ICS.format(startsAt.plus(Duration.ofMinutes(45))))
                    .contains("SUMMARY:Entrevista – " + job.title()).doesNotContain("Tomás");
            assertThat(jdbc.queryForMap("SELECT automatic, sent_by, application_id FROM messages WHERE template_code = 'interview-invite-email' AND candidate_id = ?",
                    app.candidateId())).containsEntry("automatic", true).containsEntry("sent_by", recruiter.id()).containsEntry("application_id", app.id());

            // Interviewer: bell and email with the .ics; the recruiter who acted is not told
            assertThat(Bell.of(jdbc, interviewer.id())).singleElement().satisfies(bell -> {
                assertThat(bell.type()).isEqualTo("INTERVIEW_ASSIGNED");
                assertThat(bell.body()).contains("Tomás Bento Teste").contains(job.title()).contains(luanda(startsAt));
                assertThat(bell.link()).isEqualTo("/app/entrevistas");
            });
            assertThat(ics(interviewer.email(), "Nova entrevista atribuída")).contains("METHOD:REQUEST");
            assertThat(Bell.of(jdbc, recruiter.id()).stream().filter(b -> b.type().startsWith("INTERVIEW"))).isEmpty();
        }

        @Test
        void theScheduleIsValidatedLikeTheFrontend() throws Exception {
            Job job = openJob();
            App app = apply(job);
            Map<String, Object> body = fixed(app, future(2, 10), interviewer);

            body.put("startsAt", clock.instant().minus(Duration.ofHours(1)).toString());
            create(body).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("startsAt"));
            body.remove("startsAt");
            create(body).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value("Indique a data da entrevista ou proponha horários."));
            body.put("proposedSlots", List.of(future(2, 10).toString()));
            create(body).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("proposedSlots"));
            body.remove("proposedSlots");
            body.put("startsAt", future(2, 10).toString());

            Map<String, Object> onsite = new HashMap<>(body);
            onsite.put("location", " ");
            create(onsite).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("location"));

            // A3: the link must be on the provider's host, https only
            Map<String, Object> video = new HashMap<>(body);
            video.put("mode", "video");
            video.put("meetingProvider", "meet");
            video.put("meetingUrl", "https://teams.microsoft.com/l/meetup-join/abc");
            create(video).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("meetingUrl"));
            video.put("meetingUrl", "http://meet.google.com/abc-defg-hij");
            create(video).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("meetingUrl"));
            video.put("meetingProvider", "teams");
            video.put("meetingUrl", "https://teams.live.com/meet/9876");
            JsonNode teams = created(video);
            assertThat(teams.path("location").isNull()).isTrue();
            assertThat(teams.path("meetingUrl").asString()).isEqualTo("https://teams.live.com/meet/9876");

            // Interviewers: active staff only; duration 15–240
            TestUser inactive = users.create(UserRole.INTERVIEWER);
            jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", inactive.id());
            Map<String, Object> staff = fixed(app, future(4, 10), inactive);
            create(staff).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("interviewerIds"));
            staff.put("interviewerIds", List.of(clientUser.id().toString()));
            create(staff).andExpect(status().isUnprocessableContent());
            staff.put("interviewerIds", List.of(interviewer.id().toString()));
            staff.put("durationMinutes", 300);
            create(staff).andExpect(status().isBadRequest());

            // A client interview needs the candidate shared with the client
            Map<String, Object> client = fixed(app, future(5, 10), interviewer);
            client.put("type", "client");
            create(client).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("type"));

            // Only administrators and recruiters schedule
            api.sendJson("POST", INTERVIEWS, users.accessToken(interviewer), fixed(app, future(6, 10), interviewer)).andExpect(status().isForbidden());
        }

        @Test
        void overlappingInterviewsOfTheSameInterviewerOrCandidateAskForConfirmation() throws Exception {
            Job job = openJob();
            App app = apply(job);
            App other = apply(job);
            Instant startsAt = future(3, 10);
            created(fixed(app, startsAt, interviewer));

            // Same interviewer, 30 minutes later (overlaps the 45 minutes) → 409 with the conflict in Luanda time
            Map<String, Object> overlapping = fixed(other, startsAt.plus(Duration.ofMinutes(30)), interviewer);
            JsonNode conflict = json(create(overlapping).andExpect(status().isConflict()));
            assertThat(conflict.path("message").asString()).isEqualTo("Há entrevistas sobrepostas neste horário. Confirme para marcar mesmo assim.");
            assertThat(conflict.path("errors").get(0).path("field").asString()).isEqualTo("interviewerIds");
            assertThat(conflict.path("errors").get(0).path("message").asString()).contains("(hora de Luanda)");

            // Same candidate with another interviewer → conflict on the candidate
            JsonNode candidate = json(create(fixed(app, startsAt.plus(Duration.ofMinutes(15)), jobInterviewer)).andExpect(status().isConflict()));
            assertThat(candidate.path("errors").get(0).path("field").asString()).isEqualTo("startsAt");
            assertThat(candidate.path("errors").get(0).path("message").asString()).startsWith("O candidato já tem uma entrevista em");

            // Right after the first one: no conflict; or confirmed with ignoreConflicts
            created(fixed(other, startsAt.plus(Duration.ofMinutes(45)), interviewer));
            overlapping.put("ignoreConflicts", true);
            created(overlapping);
        }
    }

    // =====================================================================================

    @Nested
    class ChangesAndStatus {

        @Test
        void reschedulingKeepsTheCalendarUidRaisesTheSequenceAndCancellingSendsTheCancellation() throws Exception {
            Job job = openJob();
            App app = apply(job);
            JsonNode interview = created(fixed(app, future(3, 9), interviewer));
            String id = interview.path("id").asString();

            // Stale version → 409
            Instant later = future(4, 14);
            patch("/" + id, Map.of("startsAt", later.toString(), "version", 5, "notifyCandidate", true)).andExpect(status().isConflict());

            JsonNode moved = json(patch("/" + id, Map.of("startsAt", later.toString(), "version", 0, "notifyCandidate", true)).andExpect(status().isOk()));
            assertThat(Instant.parse(moved.path("startsAt").asString())).isEqualTo(later);
            await().atMost(Duration.ofSeconds(10)).until(() -> count("SELECT count(*) FROM outbound_emails WHERE recipient = ? AND subject LIKE 'Convite%'", app.email()) == 2);
            assertThat(ics(app.email(), "Convite para entrevista")).contains("UID:interview-" + id + "@").contains("SEQUENCE:1").contains("DTSTART:" + ICS.format(later));
            assertThat(Bell.of(jdbc, interviewer.id()).getFirst().type()).isEqualTo("INTERVIEW_UPDATED");

            // Another interviewer: the new one is invited, the removed one gets the cancellation
            TestUser replacement = users.create(UserRole.INTERVIEWER);
            long version = moved.path("version").asLong();
            patch("/" + id, Map.of("interviewerIds", List.of(replacement.id().toString()), "version", version)).andExpect(status().isOk());
            assertThat(Bell.of(jdbc, replacement.id()).getFirst().type()).isEqualTo("INTERVIEW_ASSIGNED");
            assertThat(Bell.of(jdbc, interviewer.id()).getFirst().type()).isEqualTo("INTERVIEW_CANCELLED");
            assertThat(ics(interviewer.email(), "Entrevista cancelada")).contains("METHOD:CANCEL").contains("STATUS:CANCELLED");

            // Result before the time → 422
            long current = jdbc.queryForObject("SELECT version FROM interviews WHERE id = ?::uuid", Long.class, id);
            patch("/" + id + "/status", Map.of("status", "done", "version", current))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Só pode registar o resultado a partir da hora da entrevista."));

            // Cancelled, the candidate told → cancellation email with METHOD:CANCEL and a higher SEQUENCE
            JsonNode cancelled = json(patch("/" + id + "/status", Map.of("status", "cancelled", "notes", "Cancelada: indisponibilidade", "notify", true, "version", current))
                    .andExpect(status().isOk()));
            assertThat(cancelled.path("status").asString()).isEqualTo("cancelled");
            assertThat(cancelled.path("notes").asString()).isEqualTo("Cancelada: indisponibilidade");
            awaitSubject(app.email(), "Entrevista cancelada – " + job.title());
            assertThat(ics(app.email(), "Entrevista cancelada")).contains("METHOD:CANCEL").contains("SEQUENCE:3").contains("UID:interview-" + id + "@");

            // Final
            patch("/" + id, Map.of("startsAt", future(5, 9).toString(), "version", cancelled.path("version").asLong()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Esta entrevista já foi concluída ou cancelada."));
        }

        @Test
        void doneAndNoShowOnlyFromTheStartTime() throws Exception {
            Job job = openJob();
            App app = apply(job);
            JsonNode interview = created(fixed(app, future(1, 9), interviewer));
            clock.advance(Duration.ofDays(2));

            JsonNode done = json(patch("/" + interview.path("id").asString() + "/status", Map.of("status", "done", "version", interview.path("version").asLong()))
                    .andExpect(status().isOk()));
            assertThat(done.path("status").asString()).isEqualTo("done");
            patch("/" + interview.path("id").asString() + "/status", Map.of("status", "no_show", "version", done.path("version").asLong()))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        void proposedSlotsWaitForAChoiceTheFirstChoiceWinsAndPastSlotsAreRefused() throws Exception {
            Job job = openJob();
            App app = apply(job);
            Instant first = future(2, 9);
            Instant second = future(3, 15);
            Map<String, Object> body = fixed(app, first, interviewer);
            body.remove("startsAt");
            body.put("proposedSlots", List.of(second.toString(), first.toString()));
            JsonNode interview = created(body);
            String id = interview.path("id").asString();
            assertThat(interview.path("status").asString()).isEqualTo("pending_slot");
            assertThat(interview.path("startsAt").isNull()).isTrue();
            assertThat(Instant.parse(interview.path("proposedSlots").get(0).asString())).isEqualTo(first);

            // The candidate is asked to choose (no calendar yet)
            Email choose = awaitSubject(app.email(), "Escolha o horário da sua entrevista – " + job.title());
            assertThat(choose.text()).contains("escolha o que lhe for mais conveniente");
            assertThat(ics(app.email(), "Escolha o horário")).isNull();

            // A slot that was not proposed → 422; two staff "Fixar" at the same time → one wins, the other 409
            api.sendJson("POST", INTERVIEWS + "/" + id + "/choose-slot", users.accessToken(recruiter), Map.of("slot", future(6, 9).toString()))
                    .andExpect(status().isUnprocessableContent());
            List<Integer> statuses = concurrently(List.of(
                    () -> api.sendJson("POST", INTERVIEWS + "/" + id + "/choose-slot", users.accessToken(recruiter), Map.of("slot", first.toString()))
                            .andReturn().getResponse().getStatus(),
                    () -> api.sendJson("POST", INTERVIEWS + "/" + id + "/choose-slot", users.accessToken(recruiter), Map.of("slot", second.toString()))
                            .andReturn().getResponse().getStatus()));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            api.sendJson("POST", INTERVIEWS + "/" + id + "/choose-slot", users.accessToken(recruiter), Map.of("slot", first.toString()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("O horário já foi escolhido."));
            awaitSubject(app.email(), "Convite para entrevista – " + job.title());

            // A slot already in the past → 422
            Map<String, Object> soon = fixed(app, first, interviewer);
            soon.remove("startsAt");
            Instant nearSlot = clock.instant().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MINUTES);
            soon.put("proposedSlots", List.of(nearSlot.toString(), future(8, 9).toString()));
            soon.put("ignoreConflicts", true);
            String pending = created(soon).path("id").asString();
            clock.advance(Duration.ofHours(3));
            api.sendJson("POST", INTERVIEWS + "/" + pending + "/choose-slot", users.accessToken(recruiter), Map.of("slot", nearSlot.toString()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Este horário já passou. Escolha outro."));
        }

        @Test
        void aClientInterviewTellsTheClientWithoutTheCandidateName() throws Exception {
            Job job = openJob();
            App app = apply(job);
            api.sendJson("POST", "/api/v1/applications/share-with-client", users.accessToken(recruiter), Map.of("ids", List.of(app.id().toString())))
                    .andExpect(status().isOk());
            Instant first = future(3, 9);
            Map<String, Object> body = fixed(app, first, interviewer);
            body.put("type", "client");
            body.remove("startsAt");
            body.put("proposedSlots", List.of(first.toString(), future(4, 9).toString()));
            JsonNode interview = created(body);
            assertThat(interview.path("clientParticipantIds")).hasSize(1);
            assertThat(interview.path("clientParticipantIds").get(0).asString()).isEqualTo(clientUser.id().toString());

            // The client chooses (A1): bell + email "Escolha o horário"; the candidate gets nothing yet
            assertThat(Bell.of(jdbc, clientUser.id()).stream().filter(b -> b.type().startsWith("INTERVIEW"))).singleElement().satisfies(bell -> {
                assertThat(bell.title()).isEqualTo("Escolha o horário da entrevista");
                assertThat(bell.body()).doesNotContain("Tomás");
                assertThat(bell.link()).isEqualTo("/cliente/entrevistas");
            });
            assertThat(count("SELECT count(*) FROM messages WHERE application_id = ? AND template_code = 'interview-slots-email'", app.id())).isZero();

            // Fixed by the staff: the candidate gets the invitation, the client the calendar without the name
            api.sendJson("POST", INTERVIEWS + "/" + interview.path("id").asString() + "/choose-slot", users.accessToken(recruiter), Map.of("slot", first.toString()))
                    .andExpect(status().isOk());
            awaitSubject(app.email(), "Convite para entrevista");
            Email client = awaitSubject(clientUser.email(), "Entrevista alterada");
            assertThat(client.text()).contains(job.title()).doesNotContain("Tomás");
            assertThat(ics(clientUser.email(), "Entrevista alterada")).contains("METHOD:REQUEST").doesNotContain("Tomás");
        }
    }

    // =====================================================================================

    @Nested
    class AccessAndCancellation {

        @Test
        void anInterviewerOfAnInterviewSeesThatCandidateAndOnlyTheirOwnInterviews() throws Exception {
            Job job = openJob();
            App app = apply(job);
            JsonNode interview = created(fixed(app, future(3, 9), interviewer));
            String id = interview.path("id").asString();

            // Not assigned to the job, but interviewer of this interview: restricted candidate page and application
            mvc.perform(get("/api/v1/candidates/" + app.candidateId() + "/profile").with(users.bearer(interviewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.restricted").value(true))
                    .andExpect(jsonPath("$.applications.length()").value(1));
            mvc.perform(get("/api/v1/applications/" + app.id()).with(users.bearer(interviewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.candidate.email").isEmpty());
            mvc.perform(get("/api/v1/jobs/" + job.id() + "/applications").with(users.bearer(interviewer))).andExpect(status().isForbidden());

            // Agenda: an interviewer only gets their own (the filter cannot widen it)
            JsonNode mine = json(mvc.perform(get(INTERVIEWS).param("interviewerId", jobInterviewer.id().toString()).with(users.bearer(interviewer))));
            assertThat(mine).isNotEmpty();
            mine.forEach(i -> assertThat(i.path("interviewerIds").toString()).contains(interviewer.id().toString()));
            assertThat(json(mvc.perform(get(INTERVIEWS).param("applicationId", app.id().toString()).with(users.bearer(jobInterviewer))))).isEmpty();
            mvc.perform(get(INTERVIEWS + "/" + id).with(users.bearer(jobInterviewer)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Esta entrevista não lhe foi atribuída."));
            mvc.perform(get(INTERVIEWS + "/" + id).with(users.bearer(interviewer))).andExpect(status().isOk());
            api.sendJson("PATCH", INTERVIEWS + "/" + id + "/status", users.accessToken(interviewer), Map.of("status", "cancelled", "version", 0))
                    .andExpect(status().isForbidden());

            // Filters of the staff agenda
            JsonNode range = json(mvc.perform(get(INTERVIEWS).param("from", future(3, 0).toString()).param("to", future(4, 0).toString())
                    .param("applicationId", app.id().toString()).param("status", "scheduled").with(users.bearer(recruiter))));
            assertThat(range).hasSize(1);
            assertThat(json(mvc.perform(get(INTERVIEWS).param("clientId", clientId.toString()).param("jobId", job.id().toString()).with(users.bearer(recruiter)))))
                    .hasSize(1);
            assertThat(json(mvc.perform(get(INTERVIEWS).param("clientId", UUID.randomUUID().toString()).param("jobId", job.id().toString())
                    .with(users.bearer(recruiter))))).isEmpty();
            mvc.perform(get(INTERVIEWS).with(users.bearer(clientUser))).andExpect(status().isForbidden());
        }

        @Test
        void rejectingTheApplicationOrClosingTheJobCancelsTheInterviewsStillToHappen() throws Exception {
            Job job = openJob();
            App rejected = apply(job);
            App open = apply(job);
            String first = created(fixed(rejected, future(3, 9), interviewer)).path("id").asString();
            String second = created(fixed(open, future(3, 11), interviewer)).path("id").asString();

            UUID reason = jdbc.queryForObject("SELECT id FROM rejection_reasons WHERE code = 'client'", UUID.class);
            api.sendJson("POST", "/api/v1/applications/reject", users.accessToken(recruiter), Map.of("ids", List.of(rejected.id().toString()), "reasonId", reason.toString()))
                    .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM interviews WHERE id = ?::uuid", String.class, first)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT status FROM interviews WHERE id = ?::uuid", String.class, second)).isEqualTo("SCHEDULED");
            Email cancellation = awaitSubject(rejected.email(), "Entrevista cancelada – " + job.title());
            assertThat(cancellation.text()).contains("foi cancelada").doesNotContain("rejeit");
            assertThat(Bell.of(jdbc, interviewer.id()).getFirst().type()).isEqualTo("INTERVIEW_CANCELLED");

            // Paused: nothing; closed: cancelled
            String token = users.accessToken(recruiter);
            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", token, Map.of("status", "paused")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM interviews WHERE id = ?::uuid", String.class, second)).isEqualTo("SCHEDULED");
            api.sendJson("PATCH", "/api/v1/jobs/" + job.id() + "/status", token, Map.of("status", "closed")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT status FROM interviews WHERE id = ?::uuid", String.class, second)).isEqualTo("CANCELLED");
            awaitSubject(open.email(), "Entrevista cancelada – " + job.title());
        }

        @Test
        void erasureCancelsWithoutTheNameAndBlanksTheNotes() throws Exception {
            Job job = openJob();
            App app = apply(job);
            Map<String, Object> body = fixed(app, future(3, 9), interviewer);
            body.put("notes", "Pediu para ser ao fim da tarde.");
            String id = created(body).path("id").asString();

            erasureService.anonymize(app.candidateId(), ErasureLog.Reason.HOLDER_REQUEST, null);

            Map<String, Object> row = jdbc.queryForMap("SELECT status, notes, starts_at FROM interviews WHERE id = ?::uuid", id);
            assertThat(row.get("status")).isEqualTo("CANCELLED");
            assertThat(row.get("notes")).isNull();
            assertThat(row.get("starts_at")).isNotNull();
            assertThat(Bell.of(jdbc, interviewer.id()).getFirst()).satisfies(bell -> {
                assertThat(bell.type()).isEqualTo("INTERVIEW_CANCELLED");
                assertThat(bell.body()).contains("um candidato").doesNotContain("Tomás");
            });
            assertThat(count("SELECT count(*) FROM messages WHERE candidate_id = ?", app.candidateId())).isZero();
        }
    }

    // =====================================================================================

    static List<Integer> concurrently(List<java.util.concurrent.Callable<Integer>> calls) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (var call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
        }
        return results;
    }
}
