package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.JobBodies;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class RecommendedJobsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Test
    void visibleJobsOrderedByTheMatchOfTheOwnProfile() throws Exception {
        CandidateApi candidates = new CandidateApi(mvc, users, userService);
        String skill = "Competencia" + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
        TestUser candidate = candidates.verifiedCandidate();
        candidates.updateProfile(candidate, Map.of("skills", List.of(skill), "driverLicenses", List.of("B")));

        TestUser recruiter = users.create(UserRole.RECRUITER);
        UUID clientId = users.createClient();
        Map<String, Object> matching = JobBodies.draft(clientId, recruiter.id(), users.create(UserRole.INTERVIEWER).id());
        matching.put("title", "Vaga Ideal " + skill);
        matching.put("status", "open");
        matching.put("requirements", List.of(
                JobBodies.requirement(null, "Domina " + skill, "mandatory", 5, "skill", skill, null),
                JobBodies.requirement(null, "Carta B", "desirable", 1, "license", "B", null)));
        JsonNode ideal = json(candidates.api().sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), matching).andExpect(status().isCreated()));
        Map<String, Object> draft = JobBodies.draft(clientId, recruiter.id(), users.create(UserRole.INTERVIEWER).id());
        draft.put("title", "Rascunho " + skill);
        draft.put("requirements", matching.get("requirements"));
        candidates.api().sendJson("POST", "/api/v1/jobs", users.accessToken(recruiter), draft).andExpect(status().isCreated());

        JsonNode recommended = json(mvc.perform(get("/api/v1/me/recommended-jobs").param("limit", "10").with(users.bearer(candidate)))
                .andExpect(status().isOk()));

        assertThat(recommended.get(0).path("id").asString()).isEqualTo(ideal.path("id").asString());
        assertThat(recommended.get(0).path("match").asInt()).isEqualTo(100);
        assertThat(recommended.get(0).has("clientId")).isFalse();
        assertThat(recommended.get(0).path("requirements").get(0).has("weight")).isFalse();
        assertThat(recommended.toString()).doesNotContain("Rascunho " + skill);
        assertThat(recommended.size()).isLessThanOrEqualTo(10);
        assertThat(json(mvc.perform(get("/api/v1/me/recommended-jobs").with(users.bearer(candidate)))).size()).isLessThanOrEqualTo(3);
        mvc.perform(get("/api/v1/me/recommended-jobs").param("limit", "11").with(users.bearer(candidate))).andExpect(status().isBadRequest());
    }
}
