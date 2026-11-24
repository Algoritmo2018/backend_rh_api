package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureService;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageService;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import com.minhaempresa.recrutamentoapi.support.TestCandidateAccess;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class CandidateErasureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    CandidateErasureService erasureService;

    @Autowired
    StorageService storage;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestCandidateAccess contributors;

    @Test
    void anonymizingRemovesPersonalDataAndFilesAndKeepsAProofWithoutPersonalData() throws Exception {
        CandidateApi candidates = new CandidateApi(mvc, users, userService);
        TestUser owner = candidates.verifiedCandidate();
        JsonNode profile = candidates.updateProfile(owner, Map.of(
                "fullName", "Edna Mbala Kiala", "phone", "923456781", "headline", "Assistente Administrativa", "skills", List.of("Excel"),
                "experience", List.of(Map.of("company", "ACME", "role", "Assistente", "startDate", "2018-01", "endDate", "2020-01", "current", false))));
        UUID candidateId = UUID.fromString(profile.path("id").asString());
        candidates.setConsent(owner, "talent_pool", true);
        for (String type : List.of("cv", "photo")) {
            byte[] content = type.equals("cv") ? SampleFiles.pdf() : SampleFiles.png(1_000);
            mvc.perform(multipart("/api/v1/me/documents").file(new MockMultipartFile("file", "f", "application/octet-stream", content))
                    .param("type", type).with(users.bearer(owner))).andExpect(status().isCreated());
        }
        TestUser recruiter = users.create(UserRole.RECRUITER);
        candidates.api().sendJson("POST", "/api/v1/references", users.accessToken(recruiter), Map.of("candidateId", candidateId.toString(),
                "name", "Maria Fernanda Lopes", "company", "ACME", "role", "Chefe", "relationship", "Chefia", "phone", "923111444")).andExpect(status().isCreated());
        String cvId = jdbc.queryForObject("SELECT cv_document_id FROM candidates WHERE id = ?", String.class, candidateId);
        mvc.perform(get("/api/v1/documents/" + cvId + "/file").with(users.bearer(recruiter))).andExpect(status().isOk());
        List<String> keys = jdbc.queryForList("SELECT storage_key FROM documents WHERE candidate_id = ?", String.class, candidateId);
        TestUser admin = users.create(UserRole.ADMIN);

        erasureService.anonymize(candidateId, ErasureLog.Reason.HOLDER_REQUEST, admin.id());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM candidates WHERE id = ?", candidateId);
        assertThat(row.get("full_name")).isEqualTo("Candidato removido");
        assertThat((String) row.get("email")).startsWith("removido-").endsWith("@anonimizado.invalid");
        assertThat(row.get("phone")).isEqualTo("");
        assertThat(row.get("headline")).isEqualTo("");
        assertThat(row.get("search_text")).isEqualTo("");
        assertThat(row.get("in_talent_pool")).isEqualTo(false);
        assertThat(row.get("cv_document_id")).isNull();
        assertThat(row.get("photo_document_id")).isNull();
        for (String table : List.of("documents", "work_experiences", "candidate_languages", "candidate_references")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE candidate_id = ?", Long.class, candidateId)).as(table).isZero();
        }
        // Consents: history kept, nothing active
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consents WHERE candidate_id = ? AND revoked_at IS NULL", Long.class, candidateId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consents WHERE candidate_id = ?", Long.class, candidateId)).isEqualTo(1L);
        // Access log kept (who opened what), without the deleted document
        assertThat(jdbc.queryForList("SELECT document_id FROM document_access_log WHERE candidate_id = ?", candidateId))
                .hasSize(1).allSatisfy(r -> assertThat(r.get("document_id")).isNull());
        // Files leave the bucket after the commit
        for (String key : keys) {
            Awaitility.await().untilAsserted(() -> assertThatThrownBy(() -> storage.load(key)).isInstanceOf(NoSuchElementException.class));
        }
        // Other modules were asked to erase their data
        assertThat(contributors.wasErased(candidateId)).isTrue();

        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM erasure_log ORDER BY erased_at DESC LIMIT 1");
        assertThat(log.get("reason")).isEqualTo("HOLDER_REQUEST");
        assertThat(log.get("executed_by")).isEqualTo(admin.id());
        assertThat(log.get("documents_deleted")).isEqualTo(2);
        assertThat(log.keySet()).containsExactlyInAnyOrder("id", "erased_at", "reason", "executed_by", "documents_deleted");
        assertThat(log.values().toString()).doesNotContain(candidateId.toString()).doesNotContain("Edna").doesNotContain(owner.email());

        // The talent search no longer finds the person
        JsonNode search = json(mvc.perform(get("/api/v1/candidates/search").param("q", "Edna Mbala").with(users.bearer(recruiter))));
        assertThat(search.toString()).doesNotContain(candidateId.toString());
    }

    @Test
    void theRetentionJobHasNoExecutor() {
        TestUser owner = new CandidateApi(mvc, users, userService).verifiedCandidate();
        UUID candidateId = jdbc.queryForObject("SELECT candidate_id FROM users WHERE id = ?", UUID.class, owner.id());

        erasureService.anonymize(candidateId, ErasureLog.Reason.RETENTION, null);

        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM erasure_log ORDER BY erased_at DESC LIMIT 1");
        assertThat(log.get("reason")).isEqualTo("RETENTION");
        assertThat(log.get("executed_by")).isNull();
        assertThat(log.get("documents_deleted")).isEqualTo(0);
    }
}
