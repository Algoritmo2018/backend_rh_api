package com.minhaempresa.recrutamentoapi.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureService;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.Bell;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** Stage 8c: sharing applications with the client company (staff side and the read-only client portal). */
@IntegrationTest
class ClientSharingIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String APPLICATIONS = "/api/v1/applications";
    static final String PORTAL = "/api/v1/client-portal";

    /** Fields the client must never receive, at any depth (approved list, stage 8c). */
    static final Set<String> NEVER_SEEN = Set.of("email", "phone", "whatsapp", "birthDate", "gender", "nationality", "isAngolan", "salaryExpectation",
            "cvDocumentId", "cvUrl", "documents", "references", "messages", "rating", "tagIds", "eliminated", "screeningAnswers", "matches", "sourceId",
            "source", "rejectionReasonId", "rejectionNote", "rejectedAt", "rejectedById", "userId", "candidateId", "inTalentPool", "version", "history",
            "clientCvDocumentId", "authorId");

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CandidateErasureService erasureService;

    AuthApi api;
    CandidateApi candidates;
    TestUser recruiter;
    TestUser interviewer;
    UUID clientId;
    TestUser clientUser;

    record Job(UUID id, String title, String rotationQuestion) {}

    record App(UUID id, UUID candidateId, String email, String phone) {}

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        candidates = new CandidateApi(mvc, users, userService);
        recruiter = users.create(UserRole.RECRUITER);
        interviewer = users.create(UserRole.INTERVIEWER);
        clientId = users.createClient();
        clientUser = users.createClientUser(clientId);
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    Job openJob() throws Exception {
        Map<String, Object> body = JobBodies.draft(clientId, recruiter.id(), interviewer.id());
        String title = "Soldador " + UUID.randomUUID().toString().substring(0, 6);
        body.put("title", title);
        body.put("status", "open");
        JsonNode job = json(api.sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), body).andExpect(status().isCreated()));
        return new Job(UUID.fromString(job.path("id").asString()), title, job.path("screeningQuestions").get(0).path("id").asString());
    }

    App applyPublic(Job job, String email, Map<String, Object> quickProfile) throws Exception {
        String phone = TestUsers.uniqueLocalPhone();
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", job.id().toString());
        body.put("fullName", "Rosa Cardoso Teste");
        body.put("email", email);
        body.put("phone", phone);
        body.put("province", "Benguela");
        body.put("screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim")));
        body.put("consentJob", true);
        body.put("consentTalentPool", true);
        body.put("source", "portal");
        if (quickProfile != null) {
            body.put("quickProfile", quickProfile);
        }
        var request = multipart("/api/v1/public/applications")
                .file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(body)));
        if (quickProfile == null) {
            request.file(new MockMultipartFile("cv", "cv-original.pdf", "application/pdf", SampleFiles.pdf()));
        }
        mvc.perform(request.with(TestUsers.randomIp())).andExpect(status().isAccepted());
        Map<String, Object> row = jdbc.queryForMap("SELECT id, candidate_id FROM applications WHERE job_id = ? AND email = ?", job.id(), email);
        return new App((UUID) row.get("id"), (UUID) row.get("candidate_id"), email, phone);
    }

    App applyPublic(Job job) throws Exception {
        return applyPublic(job, TestUsers.uniqueEmail(), null);
    }

    static Map<String, Object> quickProfile() {
        Map<String, Object> quick = new HashMap<>();
        quick.put("headline", "Soldador TIG");
        quick.put("skills", List.of("Soldadura TIG", "Leitura de desenhos"));
        quick.put("years", 7);
        quick.put("educationLevel", "technical");
        quick.put("lastRole", "Soldador");
        quick.put("lastCompany", "Estaleiro Y");
        quick.put("driverLicenses", List.of("C"));
        return quick;
    }

    ResultActions share(List<UUID> ids, String summary) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", ids.stream().map(UUID::toString).toList());
        body.put("summary", summary);
        return api.sendJson("POST", APPLICATIONS + "/share-with-client", users.accessToken(recruiter), body);
    }

    /** The candidate's consent for this job now points to a text version that does not cover sharing. */
    void consentWithoutSharing(App app, UUID jobId) {
        String version = "T" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO consent_text_versions (version, job_text, talent_pool_text, includes_client_sharing, created_at) VALUES (?, ?, ?, FALSE, now())",
                version, "Autorizo o tratamento dos meus dados para esta vaga, sem partilha.", "Banco de talentos durante 24 meses.");
        jdbc.update("UPDATE consents SET text_version = ? WHERE candidate_id = ? AND job_id = ? AND type = 'JOB_APPLICATION'", version, app.candidateId(), jobId);
    }

    ResultActions portal(String path, TestUser user) throws Exception {
        return mvc.perform(get(PORTAL + path).with(users.bearer(user)));
    }

    long version(UUID id) {
        return jdbc.queryForObject("SELECT version FROM applications WHERE id = ?", Long.class, id);
    }

    static JsonNode json(ResultActions actions) throws Exception {
        String content = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? JSON.missingNode() : JSON.readTree(content);
    }

    /** Every field name at any depth of the JSON. */
    static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        collect(node, names);
        return names;
    }

    private static void collect(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                names.add(entry.getKey());
                collect(entry.getValue(), names);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }

    // =====================================================================================

    @Test
    void sharingChecksTheConsentOfEachApplicationAndTellsTheClientWithoutNames() throws Exception {
        Job job = openJob();
        App allowed = applyPublic(job);
        App notAllowed = applyPublic(job);
        consentWithoutSharing(notAllowed, job.id());

        JsonNode result = json(share(List.of(allowed.id(), notAllowed.id()), "Experiência sólida em estaleiro.").andExpect(status().isOk()));
        assertThat(result.path("shared").asInt()).isEqualTo(1);
        assertThat(result.path("refused")).singleElement().satisfies(refusal -> {
            assertThat(refusal.path("id").asString()).isEqualTo(notAllowed.id().toString());
            assertThat(refusal.path("message").asString()).isEqualTo("O candidato não autorizou a partilha dos seus dados com a empresa cliente.");
        });
        Map<String, Object> row = jdbc.queryForMap("SELECT shared_with_client, client_decision, recruiter_summary, shared_at FROM applications WHERE id = ?", allowed.id());
        assertThat(row).containsEntry("shared_with_client", true).containsEntry("client_decision", "PENDING")
                .containsEntry("recruiter_summary", "Experiência sólida em estaleiro.");
        assertThat(row.get("shared_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT shared_with_client FROM applications WHERE id = ?", Boolean.class, notAllowed.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM application_bulk_actions WHERE user_id = ? AND action = 'SHARE'", Integer.class, recruiter.id())).isEqualTo(1);

        // Only refused ones → 422 with the reason on each id; entering the client interview does not share it either
        share(List.of(notAllowed.id()), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("ids[0]"))
                .andExpect(jsonPath("$.errors[0].message").value("O candidato não autorizou a partilha dos seus dados com a empresa cliente."));
        api.sendJson("POST", APPLICATIONS + "/" + notAllowed.id() + "/move", users.accessToken(recruiter), Map.of("toStage", "client_interview", "version", version(notAllowed.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("client_interview"))
                .andExpect(jsonPath("$.sharedWithClient").value(false));

        // A talent-pool candidate added by a recruiter never accepted a job-application text → cannot be shared
        Job other = openJob();
        api.sendJson("POST", "/api/v1/talent-pool/add-to-job", users.accessToken(recruiter), Map.of("candidateIds", List.of(allowed.candidateId().toString()), "jobId", other.id().toString()))
                .andExpect(jsonPath("$.added").value(1));
        UUID added = jdbc.queryForObject("SELECT id FROM applications WHERE job_id = ?", UUID.class, other.id());
        share(List.of(added), null).andExpect(status().isUnprocessableContent());

        // The client: one bell and one email per user, the job title and never the candidate's name
        String expected = "Novos candidatos apresentados para a vaga " + job.title();
        assertThat(Bell.of(jdbc, clientUser.id())).singleElement().satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("CANDIDATES_SHARED");
            assertThat(bell.body()).isEqualTo(expected + ".");
            assertThat(bell.link()).isEqualTo("/cliente/vagas/" + job.id());
        });
        Email email = mailpit.awaitEmailTo(clientUser.email());
        assertThat(email.subject()).isEqualTo(expected);
        assertThat(email.text()).contains(job.title()).doesNotContain("Rosa").doesNotContain(allowed.email());
    }

    @Test
    void theClientSeesOnlyTheApprovedFieldsAndAnAnonymousApplicationNeverShowsTheStoredProfile() throws Exception {
        Job job = openJob();
        // A logged-in candidate with a full profile, private data included
        TestUser account = candidates.verifiedCandidate();
        String whatsapp = TestUsers.uniqueLocalPhone();
        Map<String, Object> profile = new HashMap<>();
        profile.put("headline", "Soldador certificado");
        profile.put("summary", "Dez anos em estaleiros navais.");
        profile.put("city", "Lobito");
        profile.put("province", "Benguela");
        profile.put("skills", List.of("Soldadura TIG"));
        profile.put("whatsapp", whatsapp);
        profile.put("gender", "M");
        profile.put("birthDate", "1988-04-12");
        profile.put("availability", "immediate");
        profile.put("driverLicenses", List.of("B"));
        profile.put("salaryExpectation", Map.of("amount", 450000, "currency", "AOA"));
        candidates.updateProfile(account, profile);
        String mine = json(mvc.perform(multipart("/api/v1/me/applications")
                        .file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, JSON.writeValueAsBytes(Map.of("jobId", job.id().toString(),
                                "consentJob", true, "consentTalentPool", false, "screeningAnswers", List.of(Map.of("questionId", job.rotationQuestion(), "answer", "sim"))))))
                        .with(users.bearer(account)))
                .andExpect(status().isCreated())).path("applicationId").asString();

        // An anonymous application (another job of the same client) with the email of that same account: only what it sent
        App anonymous = applyPublic(openJob(), account.email(), quickProfile());

        share(List.of(UUID.fromString(mine)), "Recomendado.").andExpect(status().isOk());
        share(List.of(anonymous.id()), null).andExpect(status().isOk());

        JsonNode shortlist = json(portal("/jobs/" + job.id() + "/shortlist", clientUser).andExpect(status().isOk()));
        assertThat(shortlist).hasSize(1);
        JsonNode item = shortlist.get(0);
        assertThat(fieldNames(item)).doesNotContainAnyElementsOf(NEVER_SEEN);
        assertThat(item.path("id").asString()).isEqualTo(mine);
        assertThat(item.path("stageName").asString()).isEqualTo("Nova");
        assertThat(item.path("recruiterSummary").asString()).isEqualTo("Recomendado.");
        assertThat(item.path("clientDecision").asString()).isEqualTo("pending");
        assertThat(item.path("score").isNumber()).isTrue();
        assertThat(item.path("clientCvUrl").isNull()).isTrue();
        JsonNode candidate = item.path("candidate");
        assertThat(candidate.path("summary").asString()).isEqualTo("Dez anos em estaleiros navais.");
        assertThat(candidate.path("headline").asString()).isEqualTo("Soldador certificado");
        assertThat(candidate.path("province").asString()).isEqualTo("Benguela");
        assertThat(candidate.path("availability").asString()).isEqualTo("immediate");
        assertThat(candidate.path("driverLicenses").get(0).asString()).isEqualTo("B");
        assertThat(candidate.has("city")).isTrue();
        assertThat(candidate.path("city").isNull()).isTrue();
        assertThat(candidate.has("avatarUrl")).isTrue();
        assertThat(candidate.path("avatarUrl").isNull()).isTrue();
        String raw = item.toString();
        assertThat(raw).doesNotContain(account.email()).doesNotContain(whatsapp).doesNotContain("1988-04-12").doesNotContain("450000").doesNotContain("Lobito");

        JsonNode anonymousView = json(portal("/applications/" + anonymous.id(), clientUser).andExpect(status().isOk()));
        assertThat(fieldNames(anonymousView)).doesNotContainAnyElementsOf(NEVER_SEEN);
        assertThat(anonymousView.path("candidate").path("headline").asString()).isEqualTo("Soldador TIG");
        assertThat(anonymousView.path("candidate").path("summary").isNull()).as("never the stored profile of the email owner").isTrue();
        assertThat(anonymousView.path("candidate").path("experience")).isEmpty();
        assertThat(anonymousView.path("candidate").path("totalExperienceYears").asInt()).isEqualTo(7);
        assertThat(anonymousView.path("quickProfile").path("lastCompany").asString()).isEqualTo("Estaleiro Y");
        assertThat(anonymousView.toString()).doesNotContain("Dez anos").doesNotContain(account.email()).doesNotContain(anonymous.phone());
    }

    @Test
    void rejectedWithdrawnAndErasedApplicationsDisappearFromThePortal() throws Exception {
        Job job = openJob();
        App app = applyPublic(job);
        share(List.of(app.id()), null).andExpect(status().isOk());
        portal("/applications/" + app.id(), clientUser).andExpect(status().isOk());

        Map<String, Object> reject = Map.of("ids", List.of(app.id().toString()), "reasonId",
                jdbc.queryForObject("SELECT id FROM rejection_reasons WHERE code = 'client'", UUID.class).toString());
        api.sendJson("POST", APPLICATIONS + "/reject", users.accessToken(recruiter), reject).andExpect(status().isOk());
        portal("/applications/" + app.id(), clientUser).andExpect(status().isNotFound());
        assertThat(json(portal("/jobs/" + job.id() + "/shortlist", clientUser))).isEmpty();

        api.sendJson("POST", APPLICATIONS + "/" + app.id() + "/restore", users.accessToken(recruiter), Map.of()).andExpect(status().isOk());
        portal("/applications/" + app.id(), clientUser).andExpect(status().isOk());

        api.sendJson("POST", APPLICATIONS + "/" + app.id() + "/client-comments", users.accessToken(recruiter), Map.of("text", "Disponível em janeiro."))
                .andExpect(status().isOk());
        erasureService.anonymize(app.candidateId(), ErasureLog.Reason.HOLDER_REQUEST, null);
        portal("/applications/" + app.id(), clientUser).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client_comments WHERE application_id = ?", Integer.class, app.id())).isZero();
    }

    @Test
    void theClientDownloadsOnlyTheCvForTheClientThroughTheApiAndEveryOpeningIsLogged() throws Exception {
        Job job = openJob();
        App app = applyPublic(job);
        share(List.of(app.id()), null).andExpect(status().isOk());
        UUID originalCv = jdbc.queryForObject("SELECT cv_document_id FROM applications WHERE id = ?", UUID.class, app.id());

        // Without a CV for the client: only the structured profile, no file
        assertThat(json(portal("/applications/" + app.id(), clientUser)).path("clientCvUrl").isNull()).isTrue();
        portal("/applications/" + app.id() + "/client-cv", clientUser).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/" + originalCv + "/file").with(users.bearer(clientUser))).andExpect(status().isForbidden());

        // Only a PDF up to 5 MB
        uploadClientCv(app.id(), "cv.png", SampleFiles.png(2_000)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        uploadClientCv(app.id(), "cv.pdf", SampleFiles.pdf(5 * 1024 * 1024 + 1)).andExpect(status().isUnprocessableContent());
        UUID first = UUID.fromString(json(uploadClientCv(app.id(), "cv-anonimo.pdf", SampleFiles.pdf()).andExpect(status().isOk())).path("clientCvDocumentId").asString());
        UUID second = UUID.fromString(json(uploadClientCv(app.id(), "cv-anonimo-v2.pdf", SampleFiles.pdf()).andExpect(status().isOk())).path("clientCvDocumentId").asString());
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE id = ?", Integer.class, first)).as("the replaced one is deleted").isZero();
        assertThat(jdbc.queryForObject("SELECT type FROM documents WHERE id = ?", String.class, second)).isEqualTo("CLIENT_CV");

        JsonNode view = json(portal("/applications/" + app.id(), clientUser));
        assertThat(view.path("clientCvUrl").asString()).isEqualTo("/api/v1/client-portal/applications/" + app.id() + "/client-cv");
        portal("/applications/" + app.id() + "/client-cv", clientUser)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        Map<String, Object> log = jdbc.queryForMap("SELECT user_id, document_type, action FROM document_access_log WHERE document_id = ?", second);
        assertThat(log).containsEntry("user_id", clientUser.id()).containsEntry("document_type", "CLIENT_CV").containsEntry("action", "DOWNLOAD");

        // The client cannot open it through the document endpoint, nor can a candidate upload this type
        mvc.perform(get("/api/v1/documents/" + second + "/file").with(users.bearer(clientUser))).andExpect(status().isForbidden());
        TestUser candidate = candidates.verifiedCandidate();
        mvc.perform(multipart("/api/v1/me/documents").file(new MockMultipartFile("file", "cv.pdf", "application/pdf", SampleFiles.pdf()))
                        .param("type", "client_cv").with(users.bearer(candidate)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("type"));

        // Removed → back to the profile only
        mvc.perform(delete(APPLICATIONS + "/" + app.id() + "/client-cv").with(users.bearer(recruiter))).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientCvDocumentId").isEmpty());
        portal("/applications/" + app.id() + "/client-cv", clientUser).andExpect(status().isNotFound());
    }

    ResultActions uploadClientCv(UUID applicationId, String name, byte[] content) throws Exception {
        return mvc.perform(multipart(APPLICATIONS + "/" + applicationId + "/client-cv")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content)).with(users.bearer(recruiter)));
    }

    @Test
    void feholramCommentsAreShownToTheClientAndTheClientIsToldWithoutTheName() throws Exception {
        Job job = openJob();
        App app = applyPublic(job);
        App notShared = applyPublic(job);
        api.sendJson("POST", APPLICATIONS + "/" + notShared.id() + "/client-comments", users.accessToken(recruiter), Map.of("text", "Olá"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Este candidato não foi apresentado ao cliente."));
        share(List.of(app.id()), null).andExpect(status().isOk());

        JsonNode card = json(api.sendJson("POST", APPLICATIONS + "/" + app.id() + "/client-comments", users.accessToken(recruiter),
                Map.of("text", "  Pode começar já em janeiro.  ")).andExpect(status().isOk()));
        assertThat(card.path("clientComments")).singleElement().satisfies(c -> {
            assertThat(c.path("text").asString()).isEqualTo("Pode começar já em janeiro.");
            assertThat(c.path("authorId").asString()).isEqualTo(recruiter.id().toString());
        });
        api.sendJson("POST", APPLICATIONS + "/" + app.id() + "/client-comments", users.accessToken(recruiter), Map.of("text", "x"))
                .andExpect(status().isBadRequest());

        JsonNode view = json(portal("/applications/" + app.id(), clientUser));
        assertThat(view.path("clientComments")).singleElement().satisfies(c -> {
            assertThat(c.path("text").asString()).isEqualTo("Pode começar já em janeiro.");
            assertThat(c.path("authorName").asString()).isNotBlank();
            assertThat(c.path("fromClient").asBoolean()).isFalse();
            assertThat(c.has("authorId")).isFalse();
        });
        assertThat(Bell.of(jdbc, clientUser.id()).getFirst()).satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("CLIENT_COMMENT");
            assertThat(bell.body()).isEqualTo("Nova mensagem sobre um candidato da vaga " + job.title() + ".").doesNotContain("Rosa");
        });

        // Staff views: the comment and its author on the candidate page
        JsonNode profile = json(mvc.perform(get("/api/v1/candidates/" + app.candidateId() + "/profile").with(users.bearer(recruiter))));
        JsonNode internal = profile.path("applications").get(0);
        assertThat(internal.path("clientComments")).hasSize(1);
        assertThat(internal.path("commentAuthors").path(recruiter.id().toString()).path("fromClient").asBoolean()).isFalse();
        assertThat(internal.path("commentAuthors").path(recruiter.id().toString()).path("name").asString()).isNotBlank();
    }

    @Test
    void interviewersAndClientsCannotShareCommentOrUploadAndStaffCannotUseThePortal() throws Exception {
        Job job = openJob();
        App app = applyPublic(job);
        for (TestUser user : List.of(interviewer, clientUser)) {
            api.sendJson("POST", APPLICATIONS + "/share-with-client", users.accessToken(user), Map.of("ids", List.of(app.id().toString())))
                    .andExpect(status().isForbidden());
            api.sendJson("POST", APPLICATIONS + "/" + app.id() + "/client-comments", users.accessToken(user), Map.of("text", "Olá a todos"))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart(APPLICATIONS + "/" + app.id() + "/client-cv").file(new MockMultipartFile("file", "cv.pdf", "application/pdf", SampleFiles.pdf()))
                    .with(users.bearer(user))).andExpect(status().isForbidden());
        }
        share(List.of(app.id()), null).andExpect(status().isOk());
        List<String> paths = new ArrayList<>(List.of("/jobs/" + job.id() + "/shortlist", "/applications/" + app.id(), "/applications/" + app.id() + "/client-cv"));
        for (TestUser user : List.of(recruiter, interviewer, users.create(UserRole.ADMIN), candidates.verifiedCandidate())) {
            for (String path : paths) {
                portal(path, user).andExpect(status().isForbidden());
            }
        }
        mvc.perform(post(APPLICATIONS + "/share-with-client")).andExpect(status().isUnauthorized());
    }
}
