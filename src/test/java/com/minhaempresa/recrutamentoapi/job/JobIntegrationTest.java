package com.minhaempresa.recrutamentoapi.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.Bell;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class JobIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String JOBS = "/api/v1/jobs";

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
    TestUser recruiter;
    TestUser interviewer;
    UUID clientId;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, TestUsers.randomIp());
        admin = users.create(UserRole.ADMIN);
        recruiter = users.create(UserRole.RECRUITER);
        interviewer = users.create(UserRole.INTERVIEWER);
        clientId = users.createClient("Sonangol Teste " + UUID.randomUUID().toString().substring(0, 6), true);
    }

    Map<String, Object> draft() {
        return JobBodies.draft(clientId, recruiter.id(), interviewer.id());
    }

    ResultActions send(String method, String url, TestUser as, Object body) throws Exception {
        return api.sendJson(method, url, users.accessToken(as), body);
    }

    static JsonNode json(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode create(Map<String, Object> body) throws Exception {
        return json(send("POST", JOBS, recruiter, body).andExpect(status().isCreated()));
    }

    /** The update body for a job: the form fields plus the version read. */
    static Map<String, Object> forUpdate(Map<String, Object> body, JsonNode job) {
        Map<String, Object> update = new HashMap<>(body);
        update.put("version", job.path("version").asLong());
        return update;
    }

    static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    // =====================================================================================

    @Nested
    class Create {

        @Test
        void recruiterCreatesADraftWithSanitizedDescriptionAndOrderedChildren() throws Exception {
            Map<String, Object> body = draft();
            String token = UUID.randomUUID().toString().substring(0, 6);
            body.put("title", "Técnico de HSE " + token);
            body.put("description", "<p onclick=\"x()\">Garantir as normas de <strong>segurança</strong> em todos os estaleiros.<script>alert(1)</script><img src=x onerror=y></p><a href=\"http://mal\">link</a>");

            JsonNode job = create(body);

            assertThat(job.path("status").asString()).isEqualTo("draft");
            assertThat(job.path("slug").asString()).isEqualTo("tecnico-de-hse-" + token + "-soyo");
            assertThat(job.path("publishedAt").isNull()).isTrue();
            assertThat(job.path("version").asLong()).isZero();
            assertThat(job.path("isConfidential").asBoolean()).isFalse();
            assertThat(job.path("description").asString())
                    .isEqualTo("<p>Garantir as normas de <strong>segurança</strong> em todos os estaleiros.</p>link");
            assertThat(job.path("responsibilities").get(0).asString()).isEqualTo("Inspeções diárias");
            assertThat(job.path("deadline").asString()).isEqualTo(body.get("deadline"));
            assertThat(job.path("salary").path("currency").asString()).isEqualTo("AOA");
            assertThat(job.path("requirements")).hasSize(2);
            assertThat(job.path("requirements").get(0).path("description").asString()).isEqualTo("Curso NEBOSH");
            assertThat(job.path("requirements").get(1).path("value").isNull()).isTrue();
            assertThat(job.path("requirements").get(1).path("minValue").asInt()).isEqualTo(3);
            assertThat(job.path("requirements").get(0).path("jobId").asString()).isEqualTo(job.path("id").asString());
            assertThat(job.path("screeningQuestions").get(0).path("order").asInt()).isEqualTo(1);
            assertThat(job.path("screeningQuestions").get(0).path("expectedAnswer").asString()).isEqualTo("sim");
            assertThat(job.path("screeningQuestions").get(1).path("order").asInt()).isEqualTo(2);
            assertThat(job.path("screeningQuestions").get(1).path("options")).hasSize(3);
            assertThat(job.path("interviewerIds").get(0).asString()).isEqualTo(interviewer.id().toString());

            Map<String, Object> row = jdbc.queryForMap("SELECT description_text, created_by FROM jobs WHERE id = ?::uuid", job.path("id").asString());
            assertThat(row.get("description_text")).isEqualTo("Garantir as normas de segurança em todos os estaleiros. link");
            assertThat(row.get("created_by")).isEqualTo(recruiter.id());
        }

        @Test
        void publishingOnCreateSetsPublishedAtAndTheSlugIsUnique() throws Exception {
            Map<String, Object> body = draft();
            body.put("title", "Operador de Grua " + UUID.randomUUID().toString().substring(0, 4));
            body.put("status", "open");

            JsonNode first = create(body);
            JsonNode second = create(body);

            assertThat(first.path("status").asString()).isEqualTo("open");
            assertThat(first.path("publishedAt").isNull()).isFalse();
            assertThat(second.path("slug").asString()).isEqualTo(first.path("slug").asString() + "-2");
        }

        @Test
        void onlyAdministratorsAndRecruitersManageJobs() throws Exception {
            for (UserRole role : List.of(UserRole.INTERVIEWER, UserRole.CLIENT, UserRole.CANDIDATE)) {
                TestUser user = users.create(role);
                mvc.perform(get(JOBS).with(users.bearer(user))).andExpect(status().isForbidden());
                send("POST", JOBS, user, draft()).andExpect(status().isForbidden());
            }
            mvc.perform(get(JOBS)).andExpect(status().isUnauthorized());
            send("POST", JOBS, admin, draft()).andExpect(status().isCreated());
        }

        @Test
        void businessRulesOnCreate() throws Exception {
            Map<String, Object> pastDeadline = draft();
            pastDeadline.put("deadline", LocalDate.now().minusDays(2).toString());
            send("POST", JOBS, recruiter, pastDeadline).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("deadline"))
                    .andExpect(jsonPath("$.errors[0].message").value("A data limite já passou."));

            Map<String, Object> withTest = draft();
            withTest.put("testId", UUID.randomUUID().toString());
            send("POST", JOBS, recruiter, withTest).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("testId"));

            Map<String, Object> inactiveClient = draft();
            inactiveClient.put("clientId", users.createClient("Inativa " + UUID.randomUUID(), false).toString());
            send("POST", JOBS, recruiter, inactiveClient).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("clientId"));

            Map<String, Object> interviewerAsRecruiter = draft();
            interviewerAsRecruiter.put("recruiterId", interviewer.id().toString());
            send("POST", JOBS, recruiter, interviewerAsRecruiter).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("recruiterId"));

            TestUser inactiveInterviewer = users.create(UserRole.INTERVIEWER);
            jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", inactiveInterviewer.id());
            Map<String, Object> deactivated = draft();
            deactivated.put("interviewerIds", List.of(inactiveInterviewer.id().toString()));
            send("POST", JOBS, recruiter, deactivated).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("interviewerIds"));

            Map<String, Object> candidateInterviewer = draft();
            candidateInterviewer.put("interviewerIds", List.of(users.create(UserRole.CANDIDATE).id().toString()));
            send("POST", JOBS, recruiter, candidateInterviewer).andExpect(status().isUnprocessableContent());

            Map<String, Object> unknownTag = draft();
            unknownTag.put("tagIds", List.of(UUID.randomUUID().toString()));
            send("POST", JOBS, recruiter, unknownTag).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("tagIds"));

            Map<String, Object> paused = draft();
            paused.put("status", "paused");
            send("POST", JOBS, recruiter, paused).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("status"));
        }

        @Test
        void beanValidationFollowsTheFrontendForm() throws Exception {
            Map<String, Object> body = draft();
            body.put("description", "<p>Curta</p>");
            body.put("interviewerIds", List.of());
            body.put("salary", Map.of("min", 900, "max", 100, "currency", "AOA", "period", "month", "visible", true));
            body.put("requirements", List.of(JobBodies.requirement(null, "Carta", "mandatory", 3, "license", "", null)));
            body.put("screeningQuestions", List.of(JobBodies.question(null, "Que idiomas fala?", "multiple_choice", List.of("PT", "EN"), true, true, "PT")));

            JsonNode error = json(send("POST", JOBS, recruiter, body).andExpect(status().isBadRequest()));

            Map<String, String> errors = new HashMap<>();
            error.path("errors").forEach(e -> errors.put(e.path("field").asString(), e.path("message").asString()));
            assertThat(errors)
                    .containsEntry("description", "Escreva uma descrição com pelo menos 40 caracteres.")
                    .containsEntry("interviewerIds", "Atribua pelo menos um entrevistador.")
                    .containsEntry("salary.max", "O máximo tem de ser maior que o mínimo.")
                    .containsEntry("requirements[0].value", "Indique o valor a comparar com o perfil.")
                    .containsEntry("screeningQuestions[0].eliminatory", "Só perguntas Sim/Não ou de escolha única podem ser eliminatórias.");
        }

        @Test
        void newlyAssignedStaffGetAnEmailButNotTheAuthor() throws Exception {
            Map<String, Object> body = draft();
            body.put("title", "Mecânico Industrial");
            body.put("interviewerIds", List.of(interviewer.id().toString(), recruiter.id().toString()));

            JsonNode job = json(send("POST", JOBS, admin, body).andExpect(status().isCreated()));

            Email toRecruiter = mailpit.awaitEmailTo(recruiter.email());
            assertThat(toRecruiter.subject()).isEqualTo("Nova vaga atribuída");
            assertThat(toRecruiter.text()).contains("recrutador responsável pela vaga Mecânico Industrial")
                    .contains("/app/vagas/" + job.path("id").asString());
            Email toInterviewer = mailpit.awaitEmailTo(interviewer.email());
            assertThat(toInterviewer.text()).contains("entrevistador da vaga Mecânico Industrial").contains("/app/entrevistas");
            assertThat(mailpit.countTo(admin.email())).isZero();

            String jobId = job.path("id").asString();
            assertThat(Bell.of(jdbc, recruiter.id())).singleElement().satisfies(bell -> {
                assertThat(bell.type()).isEqualTo("JOB_ASSIGNED");
                assertThat(bell.title()).isEqualTo("Nova vaga atribuída");
                assertThat(bell.body()).isEqualTo("Foi indicado como recrutador responsável pela vaga Mecânico Industrial.");
                assertThat(bell.link()).isEqualTo("/app/vagas/" + jobId);
            });
            assertThat(Bell.of(jdbc, interviewer.id())).singleElement().satisfies(bell -> {
                assertThat(bell.body()).isEqualTo("Foi indicado como entrevistador da vaga Mecânico Industrial.");
                assertThat(bell.link()).isEqualTo("/app/entrevistas");
            });
            assertThat(Bell.of(jdbc, admin.id())).isEmpty();
        }
    }

    @Nested
    class Update {

        @Test
        void childCollectionsUpdateByIdCreateTheRestAndDeleteMissing() throws Exception {
            Map<String, Object> body = draft();
            JsonNode job = create(body);
            JsonNode other = create(draft());
            String keptRequirement = job.path("requirements").get(0).path("id").asString();
            String deletedRequirement = job.path("requirements").get(1).path("id").asString();
            String foreignRequirement = other.path("requirements").get(0).path("id").asString();
            String keptQuestion = job.path("screeningQuestions").get(1).path("id").asString();

            Map<String, Object> update = forUpdate(body, job);
            update.put("requirements", List.of(
                    JobBodies.requirement("req-new-1", "Inglês fluente", "desirable", 2, "language", "Inglês", 4),
                    JobBodies.requirement(keptRequirement, "Curso NEBOSH IGC", "mandatory", 5, "skill", "NEBOSH", null),
                    JobBodies.requirement(foreignRequirement, "Primeiros socorros", "desirable", 1, "skill", "Primeiros socorros", null)));
            update.put("screeningQuestions", List.of(
                    JobBodies.question(keptQuestion, "Qual o seu nível de inglês falado?", "single_choice", List.of("Básico", "Avançado"), true, false, null)));

            JsonNode updated = json(send("PUT", JOBS + "/" + job.path("id").asString(), recruiter, update).andExpect(status().isOk()));

            List<String> requirementIds = ids(updated.path("requirements"));
            assertThat(requirementIds).hasSize(3).doesNotContain(deletedRequirement, foreignRequirement, "req-new-1");
            assertThat(requirementIds.get(1)).isEqualTo(keptRequirement);
            assertThat(updated.path("requirements").get(1).path("description").asString()).isEqualTo("Curso NEBOSH IGC");
            assertThat(updated.path("requirements").get(0).path("minValue").asInt()).isEqualTo(4);
            assertThat(ids(updated.path("screeningQuestions"))).containsExactly(keptQuestion);
            assertThat(updated.path("screeningQuestions").get(0).path("order").asInt()).isEqualTo(1);
            assertThat(updated.path("version").asLong()).isGreaterThan(job.path("version").asLong());
            // The other job keeps its requirement
            assertThat(jdbc.queryForObject("SELECT count(*) FROM job_requirements WHERE id = ?::uuid AND job_id = ?::uuid", Long.class,
                    foreignRequirement, other.path("id").asString())).isEqualTo(1L);
        }

        @Test
        void aStaleVersionIsRejectedAndEveryEditChangesTheVersion() throws Exception {
            Map<String, Object> body = draft();
            JsonNode job = create(body);
            String url = JOBS + "/" + job.path("id").asString();

            Map<String, Object> onlyQuestions = forUpdate(body, job);
            onlyQuestions.put("screeningQuestions", List.of());
            JsonNode saved = json(send("PUT", url, recruiter, onlyQuestions).andExpect(status().isOk()));
            assertThat(saved.path("version").asLong()).isGreaterThan(job.path("version").asLong());

            // Someone else still has the first version
            send("PUT", url, admin, forUpdate(body, job)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ValidationError"))
                    .andExpect(jsonPath("$.message").value("Esta vaga foi alterada por outra pessoa entretanto. Recarregue a página para ver a versão mais recente."));

            send("PUT", url, admin, forUpdate(body, saved)).andExpect(status().isOk());
        }

        @Test
        void concurrentEditsOfTheSameVersionLetOnlyOneWin() throws Exception {
            Map<String, Object> body = draft();
            JsonNode job = create(body);
            String url = JOBS + "/" + job.path("id").asString();

            List<Integer> statuses = race(() -> send("PUT", url, recruiter, forUpdate(body, job)).andReturn().getResponse().getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }

        @Test
        void slugFollowsTheTitleUntilPublishedThenStaysFixed() throws Exception {
            Map<String, Object> body = draft();
            String token = UUID.randomUUID().toString().substring(0, 6);
            body.put("title", "Soldador " + token);
            JsonNode job = create(body);
            String url = JOBS + "/" + job.path("id").asString();

            body.put("title", "Soldador Certificado " + token);
            JsonNode renamed = json(send("PUT", url, recruiter, forUpdate(body, job)).andExpect(status().isOk()));
            assertThat(renamed.path("slug").asString()).isEqualTo("soldador-certificado-" + token + "-soyo");

            JsonNode published = json(send("PATCH", url + "/status", recruiter, Map.of("status", "open")).andExpect(status().isOk()));
            body.put("title", "Soldador Sénior " + token);
            body.put("status", "open");
            JsonNode after = json(send("PUT", url, recruiter, forUpdate(body, published)).andExpect(status().isOk()));
            assertThat(after.path("slug").asString()).isEqualTo(renamed.path("slug").asString());
            assertThat(after.path("title").asString()).isEqualTo("Soldador Sénior " + token);
        }

        @Test
        void theClientOnlyChangesInADraft() throws Exception {
            Map<String, Object> body = draft();
            JsonNode job = create(body);
            String url = JOBS + "/" + job.path("id").asString();
            UUID otherClient = users.createClient();

            body.put("clientId", otherClient.toString());
            JsonNode moved = json(send("PUT", url, recruiter, forUpdate(body, job)).andExpect(status().isOk()));
            assertThat(moved.path("clientId").asString()).isEqualTo(otherClient.toString());

            JsonNode published = json(send("PATCH", url + "/status", recruiter, Map.of("status", "open")).andExpect(status().isOk()));
            body.put("clientId", clientId.toString());
            send("PUT", url, recruiter, forUpdate(body, published)).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("clientId"));
        }

        @Test
        void aDeactivatedRecruiterStaysUntilReplaced() throws Exception {
            Map<String, Object> body = draft();
            JsonNode job = create(body);
            String url = JOBS + "/" + job.path("id").asString();
            jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", recruiter.id());

            mvc.perform(get(url).with(users.bearer(admin))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.recruiterActive").value(false))
                    .andExpect(jsonPath("$.recruiterName").exists());

            // Other fields can still be edited without replacing the recruiter
            body.put("vacancies", 4);
            JsonNode edited = json(send("PUT", url, admin, forUpdate(body, job)).andExpect(status().isOk()));

            TestUser newRecruiter = users.create(UserRole.RECRUITER);
            body.put("recruiterId", newRecruiter.id().toString());
            send("PUT", url, admin, forUpdate(body, edited)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.recruiterId").value(newRecruiter.id().toString()));
        }
    }

    @Nested
    class Status {

        @Test
        void lifecycleRules() throws Exception {
            JsonNode job = create(draft());
            String url = JOBS + "/" + job.path("id").asString() + "/status";

            send("PATCH", url, recruiter, Map.of("status", "paused")).andExpect(status().isUnprocessableContent());

            JsonNode open = json(send("PATCH", url, recruiter, Map.of("status", "open")).andExpect(status().isOk()));
            String publishedAt = open.path("publishedAt").asString();
            assertThat(publishedAt).isNotBlank();

            send("PATCH", url, recruiter, Map.of("status", "draft")).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("Uma vaga publicada não pode voltar a rascunho."));
            send("PATCH", url, recruiter, Map.of("status", "paused")).andExpect(status().isOk());
            JsonNode reopened = json(send("PATCH", url, recruiter, Map.of("status", "open")).andExpect(status().isOk()));
            assertThat(reopened.path("publishedAt").asString()).isEqualTo(publishedAt);

            JsonNode closed = json(send("PATCH", url, recruiter, Map.of("status", "closed")).andExpect(status().isOk()));
            assertThat(closed.path("closedAt").isNull()).isFalse();
            send("PATCH", url, recruiter, Map.of("status", "open")).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("Uma vaga encerrada não pode ser reaberta."));
            send("PATCH", url, recruiter, Map.of("status", "closed")).andExpect(status().isOk());
        }

        @Test
        void publishingNeedsAValidDeadlineAndAnActiveClient() throws Exception {
            JsonNode job = create(draft());
            String id = job.path("id").asString();
            String url = JOBS + "/" + id + "/status";

            jdbc.update("UPDATE jobs SET deadline = ? WHERE id = ?::uuid", LocalDate.now().minusDays(1), id);
            send("PATCH", url, recruiter, Map.of("status", "open")).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("deadline"));

            jdbc.update("UPDATE jobs SET deadline = NULL WHERE id = ?::uuid", id);
            jdbc.update("UPDATE clients SET status = 'INACTIVE' WHERE id = ?", clientId);
            send("PATCH", url, recruiter, Map.of("status", "open")).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Não é possível publicar: o cliente está inativo."));
        }

        @Test
        void unknownJob() throws Exception {
            send("PATCH", JOBS + "/" + UUID.randomUUID() + "/status", recruiter, Map.of("status", "open"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Vaga não encontrada."));
        }
    }

    @Nested
    class JobRequestConversion {

        UUID requestId() throws Exception {
            TestUser clientUser = users.createClientUser(clientId);
            Map<String, Object> request = new HashMap<>();
            request.put("title", "Motorista de pesados");
            request.put("area", "logistics");
            request.put("province", "Benguela");
            request.put("vacancies", 3);
            request.put("description", "Condução de camiões entre o Lobito e Luanda.");
            request.put("requirements", "Carta de condução C e 3 anos de experiência.");
            request.put("urgency", "normal");
            JsonNode created = json(send("POST", "/api/v1/client-portal/job-requests", clientUser, request).andExpect(status().isCreated()));
            return UUID.fromString(created.path("id").asString());
        }

        @Test
        void creatingAJobFromAPendingRequestConvertsIt() throws Exception {
            UUID requestId = requestId();
            Map<String, Object> body = draft();
            body.put("clientId", users.createClient().toString()); // ignored: the client comes from the request
            body.put("jobRequestId", requestId.toString());

            JsonNode job = create(body);

            assertThat(job.path("clientId").asString()).isEqualTo(clientId.toString());
            assertThat(job.path("jobRequestId").asString()).isEqualTo(requestId.toString());
            Map<String, Object> request = jdbc.queryForMap("SELECT status, job_id FROM job_requests WHERE id = ?", requestId);
            assertThat(request.get("status")).isEqualTo("CONVERTED");
            assertThat(request.get("job_id")).isEqualTo(UUID.fromString(job.path("id").asString()));

            send("POST", JOBS, recruiter, body).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("jobRequestId"));
        }

        @Test
        void twoJobsFromTheSameRequestAtTheSameTime() throws Exception {
            UUID requestId = requestId();
            Map<String, Object> body = draft();
            body.put("jobRequestId", requestId.toString());

            List<Integer> statuses = race(() -> send("POST", JOBS, recruiter, body).andReturn().getResponse().getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs WHERE job_request_id = ?", Long.class, requestId)).isEqualTo(1L);
        }

        @Test
        void aRejectedRequestCannotBeConverted() throws Exception {
            UUID requestId = requestId();
            send("PATCH", "/api/v1/job-requests/" + requestId + "/review", admin, Map.of("status", "rejected", "note", "Sem orçamento aprovado."))
                    .andExpect(status().isOk());
            Map<String, Object> body = draft();
            body.put("jobRequestId", requestId.toString());

            send("POST", JOBS, recruiter, body).andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class Listing {

        @Test
        void filtersSearchByTitleOrClientIgnoringAccentsAndOptionalPages() throws Exception {
            String token = "Qzw" + UUID.randomUUID().toString().substring(0, 5);
            UUID accentedClient = users.createClient("Construções Ângulo " + token, true);
            Map<String, Object> body = draft();
            body.put("clientId", accentedClient.toString());
            body.put("title", "Engenheiro Civil");
            JsonNode byClient = create(body);
            Map<String, Object> titled = draft();
            titled.put("title", "Eletricista " + token);
            titled.put("status", "open");
            JsonNode byTitle = create(titled);

            JsonNode found = json(mvc.perform(get(JOBS).param("search", "construcoes angulo " + token.toLowerCase()).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(ids(found)).containsExactly(byClient.path("id").asString());
            assertThat(found.get(0).path("clientName").asString()).startsWith("Construções");
            assertThat(found.get(0).path("stageCounts").path("hr_interview").asLong()).isZero();
            assertThat(found.get(0).path("stageCounts").size()).isEqualTo(7);

            JsonNode both = json(mvc.perform(get(JOBS).param("search", token).with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(ids(both)).containsExactlyInAnyOrder(byClient.path("id").asString(), byTitle.path("id").asString());

            JsonNode open = json(mvc.perform(get(JOBS).param("search", token).param("status", "open").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(ids(open)).containsExactly(byTitle.path("id").asString());

            JsonNode page = json(mvc.perform(get(JOBS).param("search", token).param("pageSize", "1").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(page.path("total").asLong()).isEqualTo(2);
            assertThat(page.path("totalPages").asInt()).isEqualTo(2);
            assertThat(page.path("items")).hasSize(1);
            // Most recently updated first
            assertThat(page.path("items").get(0).path("id").asString()).isEqualTo(byTitle.path("id").asString());
        }

        @Test
        void clientAndTagStatsCountJobs() throws Exception {
            JsonNode tag = json(send("POST", "/api/v1/settings/tags", admin, Map.of("name", "Offshore " + UUID.randomUUID().toString().substring(0, 6), "color", "cyan"))
                    .andExpect(status().isCreated()));
            Map<String, Object> body = draft();
            body.put("tagIds", List.of(tag.path("id").asString()));
            create(body);
            body.put("status", "open");
            create(body);

            mvc.perform(get("/api/v1/clients/" + clientId).with(users.bearer(admin))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.openJobs").value(1))
                    .andExpect(jsonPath("$.totalJobs").value(2));
            mvc.perform(get("/api/v1/settings/tags/usage").with(users.bearer(admin))).andExpect(status().isOk())
                    .andExpect(jsonPath("$['" + tag.path("id").asString() + "']").value(2));

            // Deleting the tag removes it from the jobs
            send("DELETE", "/api/v1/settings/tags/" + tag.path("id").asString(), admin, null).andExpect(status().isNoContent());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM job_tags WHERE tag_id = ?::uuid", Long.class, tag.path("id").asString())).isZero();
        }
    }

    /** Runs the same call in two threads released at the same moment. */
    static List<Integer> race(ThrowingCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return call.run();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    interface ThrowingCall {
        int run() throws Exception;
    }
}
