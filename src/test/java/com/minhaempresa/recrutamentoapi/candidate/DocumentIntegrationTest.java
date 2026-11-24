package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.TestcontainersConfiguration;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageService;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestCandidateAccess;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class DocumentIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageService storage;

    @Autowired
    S3Client s3;

    @Autowired
    MinIOContainer minio;

    @Autowired
    TestCandidateAccess access;

    CandidateApi candidates;
    TestUser owner;
    String candidateId;

    @BeforeEach
    void setUp() throws Exception {
        candidates = new CandidateApi(mvc, users, userService);
        owner = candidates.verifiedCandidate();
        candidateId = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("id").asString();
    }

    ResultActions upload(TestUser user, String type, String fileName, String contentType, byte[] content, Map<String, String> params) throws Exception {
        var request = multipart("/api/v1/me/documents").file(new MockMultipartFile("file", fileName, contentType, content)).param("type", type);
        params.forEach(request::param);
        return mvc.perform(request.with(users.bearer(user)));
    }

    JsonNode uploadOk(String type, String fileName, byte[] content, Map<String, String> params) throws Exception {
        return json(upload(owner, type, fileName, "application/octet-stream", content, params).andExpect(status().isCreated()));
    }

    String storageKey(JsonNode document) {
        return jdbc.queryForObject("SELECT storage_key FROM documents WHERE id = ?::uuid", String.class, document.path("id").asString());
    }

    @Nested
    class Upload {

        @Test
        void theTypeComesFromTheContentAndTheKeyFromTheBackend() throws Exception {
            // A PDF sent as "image/png" named with folders: stored as PDF, with a clean name
            JsonNode cv = json(upload(owner, "cv", "../../Currículo Edna\".png", "image/png", SampleFiles.pdf(), Map.of()).andExpect(status().isCreated()));

            assertThat(cv.path("mimeType").asString()).isEqualTo("application/pdf");
            assertThat(cv.path("fileName").asString()).isEqualTo("Currículo Edna.pdf");
            assertThat(cv.path("status").asString()).isEqualTo("submitted");
            assertThat(cv.path("url").asString()).isEqualTo("/api/v1/documents/" + cv.path("id").asString() + "/file");
            assertThat(cv.path("candidateId").asString()).isEqualTo(candidateId);
            String key = storageKey(cv);
            assertThat(key).matches("documents/[0-9a-f-]{36}\\.pdf");
            assertThat(json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("cvDocumentId").asString())
                    .isEqualTo(cv.path("id").asString());

            JsonNode docx = uploadOk("certificate", "certificado.docx", SampleFiles.docx(), Map.of());
            assertThat(docx.path("mimeType").asString()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            assertThat(json(mvc.perform(get("/api/v1/me/documents").with(users.bearer(owner))))).hasSize(2);
        }

        @Test
        void filesAreEncryptedAtRestInAPrivateBucket() throws Exception {
            String key = storageKey(uploadOk("cv", "cv.pdf", SampleFiles.pdf(), Map.of()));

            var head = s3.headObject(HeadObjectRequest.builder().bucket(TestcontainersConfiguration.MINIO_BUCKET).key(key).build());
            assertThat(head.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);

            HttpResponse<String> anonymous = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(minio.getS3URL() + "/" + TestcontainersConfiguration.MINIO_BUCKET + "/" + key)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(anonymous.statusCode()).isEqualTo(403);
        }

        @Test
        void refusedFiles() throws Exception {
            upload(owner, "cv", "cv.exe", "application/pdf", "MZ\u0090 executable".getBytes(), Map.of()).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("file"))
                    .andExpect(jsonPath("$.errors[0].message").value("Use um ficheiro PDF, Word (DOCX), JPG ou PNG."));
            upload(owner, "cv", "cv.doc", "application/msword", SampleFiles.legacyDoc(), Map.of()).andExpect(status().isUnprocessableContent());
            upload(owner, "cv", "cv.docm", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", SampleFiles.docxWithMacros(), Map.of())
                    .andExpect(status().isUnprocessableContent());
            upload(owner, "cv", "cv.docx", "application/zip", SampleFiles.plainZip(), Map.of()).andExpect(status().isUnprocessableContent());
            upload(owner, "photo", "foto.pdf", "application/pdf", SampleFiles.pdf(), Map.of()).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("A fotografia tem de ser JPG ou PNG."));
            upload(owner, "cv", "vazio.pdf", "application/pdf", new byte[0], Map.of()).andExpect(status().isUnprocessableContent());

            upload(owner, "cv", "grande.pdf", "application/pdf", SampleFiles.pdf(5 * 1024 * 1024 + 1), Map.of()).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("O ficheiro tem de ter no máximo 5 MB."));
            upload(owner, "photo", "foto.jpg", "image/jpeg", SampleFiles.jpeg(2 * 1024 * 1024 + 1), Map.of()).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("O ficheiro tem de ter no máximo 2 MB."));
            uploadOk("certificate", "certificado.pdf", SampleFiles.pdf(5 * 1024 * 1024 + 1), Map.of());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE candidate_id = ?::uuid", Long.class, candidateId)).isEqualTo(1L);
        }

        @Test
        void identityDocumentsNeedAValidNumber() throws Exception {
            upload(owner, "bi", "bi.pdf", "application/pdf", SampleFiles.pdf(), Map.of()).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("reference"))
                    .andExpect(jsonPath("$.errors[0].message").value("Indique o número do documento."));
            upload(owner, "bi", "bi.pdf", "application/pdf", SampleFiles.pdf(), Map.of("reference", "12345")).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("O BI tem 9 dígitos, 2 letras e 3 dígitos (ex.: 004512873LA041)."));
            upload(owner, "inss", "inss.pdf", "application/pdf", SampleFiles.pdf(), Map.of("reference", "ABC")).andExpect(status().isUnprocessableContent());
            upload(owner, "certificate", "c.pdf", "application/pdf", SampleFiles.pdf(), Map.of("expiresAt", LocalDate.now().minusDays(5).toString()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("expiresAt"));

            JsonNode bi = uploadOk("bi", "bi.pdf", SampleFiles.pdf(), Map.of("reference", "004512873 la041", "expiresAt", LocalDate.now().plusYears(3).toString()));
            assertThat(bi.path("reference").asString()).isEqualTo("004512873LA041");
            // A reference sent with another type is ignored
            assertThat(uploadOk("certificate", "c.pdf", SampleFiles.pdf(), Map.of("reference", "X")).path("reference").isNull()).isTrue();
        }

        @Test
        void theProfilePhotoBecomesTheAvatar() throws Exception {
            JsonNode photo = uploadOk("photo", "eu.png", SampleFiles.png(2_000), Map.of());
            assertThat(storageKey(photo)).startsWith("photos/");
            String avatar = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("avatarUrl").asString();
            assertThat(avatar).isEqualTo("/api/v1/documents/" + photo.path("id").asString() + "/file?inline=true");
            mvc.perform(get(avatar).with(users.bearer(owner))).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/png"))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        }
    }

    @Nested
    class Access {

        @Test
        void downloadsAreSafeAndAudited() throws Exception {
            JsonNode cv = uploadOk("cv", "Currículo.pdf", SampleFiles.pdf(), Map.of());
            String url = cv.path("url").asString();
            TestUser recruiter = users.create(UserRole.RECRUITER);

            byte[] body = mvc.perform(get(url).with(users.bearer(recruiter))).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    // ASCII fallback for old clients plus the exact UTF-8 name (RFC 6266)
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"Curriculo.pdf\"; filename*=UTF-8''Curr%C3%ADculo.pdf"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(body).isEqualTo(SampleFiles.pdf());
            mvc.perform(get(url).param("inline", "true").with(users.bearer(owner))).andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
            // Word files are never opened in the browser
            JsonNode docx = uploadOk("certificate", "cert.docx", SampleFiles.docx(), Map.of());
            mvc.perform(get(docx.path("url").asString()).param("inline", "true").with(users.bearer(owner)))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));

            var log = jdbc.queryForList("SELECT user_id, action, document_type FROM document_access_log WHERE document_id = ?::uuid ORDER BY accessed_at",
                    cv.path("id").asString());
            assertThat(log).hasSize(2);
            assertThat(log.get(0)).containsEntry("user_id", recruiter.id()).containsEntry("action", "DOWNLOAD").containsEntry("document_type", "CV");
            assertThat(log.get(1)).containsEntry("user_id", owner.id()).containsEntry("action", "VIEW");
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'document_access_log'", String.class))
                    .noneMatch(column -> column.contains("ip"));
        }

        @Test
        void whoMayOpenWhat() throws Exception {
            JsonNode cv = uploadOk("cv", "cv.pdf", SampleFiles.pdf(), Map.of());
            JsonNode bi = uploadOk("bi", "bi.pdf", SampleFiles.pdf(), Map.of("reference", "004512873LA041"));
            TestUser otherCandidate = candidates.verifiedCandidate();
            TestUser interviewer = users.create(UserRole.INTERVIEWER);

            // Another candidate: "not found", like a missing id
            mvc.perform(get(cv.path("url").asString()).with(users.bearer(otherCandidate))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Documento não encontrado."));
            mvc.perform(get(cv.path("url").asString()).with(users.bearer(users.create(UserRole.CLIENT)))).andExpect(status().isForbidden());
            // Interviewer not assigned to the candidate
            mvc.perform(get(cv.path("url").asString()).with(users.bearer(interviewer))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Este candidato não lhe foi atribuído."));

            access.assign(interviewer.id(), UUID.fromString(candidateId));
            // Assigned: the CV only (conscious choice), never the identity documents
            mvc.perform(get(cv.path("url").asString()).with(users.bearer(interviewer))).andExpect(status().isOk());
            mvc.perform(get(bi.path("url").asString()).with(users.bearer(interviewer))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Não tem permissão para abrir este documento."));
            JsonNode profile = json(mvc.perform(get("/api/v1/candidates/" + candidateId + "/profile").with(users.bearer(interviewer))).andExpect(status().isOk()));
            assertThat(profile.path("restricted").asBoolean()).isTrue();
            assertThat(profile.path("cv").path("id").asString()).isEqualTo(cv.path("id").asString());
            assertThat(profile.path("candidate").path("email").isNull()).isTrue();
            assertThat(profile.path("candidate").path("avatarUrl").isNull()).isTrue();
            mvc.perform(get("/api/v1/candidates/" + candidateId + "/documents").with(users.bearer(interviewer))).andExpect(status().isForbidden());

            // Staff see every document, sensitive ones included
            TestUser recruiter = users.create(UserRole.RECRUITER);
            mvc.perform(get(bi.path("url").asString()).with(users.bearer(recruiter))).andExpect(status().isOk());
            JsonNode list = json(mvc.perform(get("/api/v1/candidates/" + candidateId + "/documents").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(list).hasSize(2);
            assertThat(list.toString()).contains("004512873LA041");
        }
    }

    @Nested
    class ReviewAndDelete {

        @Test
        void reviewIsShownToTheCandidateAndApprovedDocumentsStay() throws Exception {
            JsonNode bi = uploadOk("bi", "bi.pdf", SampleFiles.pdf(), Map.of("reference", "004512873LA041"));
            TestUser recruiter = users.create(UserRole.RECRUITER);
            String review = "/api/v1/documents/" + bi.path("id").asString() + "/review";

            candidates.api().sendJson("PATCH", review, users.accessToken(recruiter), Map.of("status", "submitted"))
                    .andExpect(status().isUnprocessableContent());
            JsonNode rejected = json(candidates.api().sendJson("PATCH", review, users.accessToken(recruiter), Map.of("status", "rejected", "note", "Ilegível"))
                    .andExpect(status().isOk()));
            assertThat(rejected.path("rejectionNote").asString()).isEqualTo("Ilegível");
            assertThat(rejected.path("reviewedById").asString()).isEqualTo(recruiter.id().toString());
            assertThat(json(mvc.perform(get("/api/v1/me/documents").with(users.bearer(owner)))).get(0).path("status").asString()).isEqualTo("rejected");

            JsonNode approved = json(candidates.api().sendJson("PATCH", review, users.accessToken(recruiter), Map.of("status", "approved", "note", "ok"))
                    .andExpect(status().isOk()));
            assertThat(approved.path("rejectionNote").isNull()).isTrue();
            mvc.perform(delete("/api/v1/me/documents/" + bi.path("id").asString()).with(users.bearer(owner))).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Um documento aprovado não pode ser apagado."));
        }

        @Test
        void deletingRemovesTheFileAfterTheCommitAndRestoresThePreviousCv() throws Exception {
            JsonNode first = uploadOk("cv", "cv-antigo.pdf", SampleFiles.pdf(), Map.of());
            JsonNode second = uploadOk("cv", "cv-novo.pdf", SampleFiles.pdf(), Map.of());
            String key = storageKey(second);

            mvc.perform(delete("/api/v1/me/documents/" + second.path("id").asString()).with(users.bearer(candidates.verifiedCandidate())))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/me/documents/" + second.path("id").asString()).with(users.bearer(owner))).andExpect(status().isNoContent());

            Awaitility.await().untilAsserted(() -> assertThatThrownBy(() -> storage.load(key)).isInstanceOf(NoSuchElementException.class));
            assertThat(storage.load(storageKey(first)).contentLength()).isPositive();
            assertThat(json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("cvDocumentId").asString())
                    .isEqualTo(first.path("id").asString());
        }
    }
}
