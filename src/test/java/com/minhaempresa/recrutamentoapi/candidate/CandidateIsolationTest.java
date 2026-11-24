package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.SampleFiles;
import org.springframework.mock.web.MockMultipartFile;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * Who sees which candidate data. Candidates only reach their own profile (always from the token); the
 * staff endpoints refuse candidates and clients; an interviewer only sees candidates assigned to them
 * (none until stage 8), without private data. Every candidate endpoint must be listed here.
 */
@IntegrationTest
class CandidateIsolationTest {

    /** Every endpoint of the candidates module and of the global search, with who may call it. */
    static final Map<String, Set<UserRole>> ENDPOINTS = Map.ofEntries(
            Map.entry("GET /api/v1/me/candidate", Set.of(UserRole.CANDIDATE)),
            Map.entry("PATCH /api/v1/me/candidate", Set.of(UserRole.CANDIDATE)),
            Map.entry("GET /api/v1/me/consents", Set.of(UserRole.CANDIDATE)),
            Map.entry("PUT /api/v1/me/consents/{type}", Set.of(UserRole.CANDIDATE)),
            Map.entry("GET /api/v1/me/recommended-jobs", Set.of(UserRole.CANDIDATE)),
            Map.entry("GET /api/v1/me/documents", Set.of(UserRole.CANDIDATE)),
            Map.entry("POST /api/v1/me/documents", Set.of(UserRole.CANDIDATE)),
            Map.entry("DELETE /api/v1/me/documents/{id}", Set.of(UserRole.CANDIDATE)),
            Map.entry("GET /api/v1/candidates/search", Set.of(UserRole.ADMIN, UserRole.RECRUITER)),
            Map.entry("GET /api/v1/candidates/skills", Set.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.CANDIDATE)),
            Map.entry("GET /api/v1/candidates/{id}/profile", Set.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.INTERVIEWER)),
            Map.entry("GET /api/v1/candidates/{id}/documents", Set.of(UserRole.ADMIN, UserRole.RECRUITER)),
            Map.entry("GET /api/v1/candidates/{id}/references", Set.of(UserRole.ADMIN, UserRole.RECRUITER)),
            Map.entry("PATCH /api/v1/documents/{id}/review", Set.of(UserRole.ADMIN, UserRole.RECRUITER)),
            Map.entry("GET /api/v1/documents/{id}/file", Set.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.INTERVIEWER, UserRole.CANDIDATE)),
            Map.entry("POST /api/v1/references", Set.of(UserRole.ADMIN, UserRole.RECRUITER)),
            Map.entry("PATCH /api/v1/references/{id}/status", Set.of(UserRole.ADMIN, UserRole.RECRUITER)),
            Map.entry("GET /api/v1/search", Set.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.INTERVIEWER)));

    /** Valid bodies, so the role check (not the validation) decides the answer. */
    static Object body(String endpoint, String candidateId) {
        return switch (endpoint) {
            case "PATCH /api/v1/me/candidate" -> Map.of();
            case "PUT /api/v1/me/consents/{type}" -> Map.of("granted", true);
            case "PATCH /api/v1/documents/{id}/review" -> Map.of("status", "approved");
            case "PATCH /api/v1/references/{id}/status" -> Map.of("status", "verified");
            case "POST /api/v1/references" -> Map.of("candidateId", candidateId, "name", "Maria Teste Silva", "company", "ACME",
                    "role", "Gestora", "relationship", "Chefia", "phone", "923000999");
            default -> null;
        };
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    CandidateApi candidates;

    @BeforeEach
    void setUp() {
        candidates = new CandidateApi(mvc, users, userService);
    }

    @Test
    void everyCandidateEndpointIsListedHere() {
        Set<String> mapped = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            String type = method.getBeanType().getPackageName();
            if (type.contains(".candidate.") || type.contains(".search.")) {
                info.getMethodsCondition().getMethods().forEach(m -> info.getPatternValues().forEach(p -> mapped.add(m.name() + " " + p)));
            }
        });
        assertThat(mapped).isEqualTo(new TreeSet<>(ENDPOINTS.keySet()));
    }

    @Test
    void onlyTheAllowedRolesReachEachEndpoint() throws Exception {
        TestUser owner = candidates.verifiedCandidate();
        String candidateId = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("id").asString();
        for (var endpoint : ENDPOINTS.entrySet()) {
            String[] parts = endpoint.getKey().split(" ");
            String path = parts[1].replace("{type}", "marketing").replace("{id}", candidateId);
            for (UserRole role : List.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.INTERVIEWER, UserRole.CLIENT, UserRole.CANDIDATE)) {
                TestUser user = role == UserRole.CANDIDATE ? candidates.verifiedCandidate() : users.create(role);
                Object body = body(endpoint.getKey(), candidateId);
                int status;
                if (endpoint.getKey().equals("POST /api/v1/me/documents")) {
                    status = mvc.perform(multipart("/api/v1/me/documents")
                                    .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", SampleFiles.pdf()))
                                    .param("type", "cv")
                                    .with(users.bearer(user)))
                            .andReturn().getResponse().getStatus();
                } else if (body == null) {
                    status = mvc.perform(request(HttpMethod.valueOf(parts[0]), path).with(users.bearer(user))).andReturn().getResponse().getStatus();
                } else {
                    status = candidates.api().sendJson(parts[0], path, users.accessToken(user), body).andReturn().getResponse().getStatus();
                }
                boolean allowedRole = endpoint.getValue().contains(role);
                boolean interviewerOnProfile = role == UserRole.INTERVIEWER && path.endsWith("/profile");
                if (allowedRole && !interviewerOnProfile) {
                    // Allowed: never refused for the role (ids of other records may still be 404)
                    assertThat(status).as(role + " " + endpoint.getKey()).isNotIn(401, 403).isLessThan(500);
                } else {
                    assertThat(status).as(role + " " + endpoint.getKey()).isEqualTo(403);
                }
            }
            int anonymous = mvc.perform(request(HttpMethod.valueOf(parts[0]), path)).andReturn().getResponse().getStatus();
            assertThat(anonymous).as("anonymous " + endpoint.getKey()).isEqualTo(401);
        }
    }

    @Test
    void anInterviewerWithoutAssignmentsSeesNoCandidate() throws Exception {
        TestUser owner = candidates.verifiedCandidate();
        JsonNode profile = candidates.updateProfile(owner, Map.of("fullName", "Kiala Interview Teste", "headline", "Técnico de redes"));
        TestUser interviewer = users.create(UserRole.INTERVIEWER);

        mvc.perform(get("/api/v1/candidates/" + profile.path("id").asString() + "/profile").with(users.bearer(interviewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Este candidato não lhe foi atribuído."));
        JsonNode search = json(mvc.perform(get("/api/v1/search").param("q", "Kiala Interview").with(users.bearer(interviewer))).andExpect(status().isOk()));
        assertThat(search.path("candidates")).isEmpty();
        assertThat(search.path("jobs")).isEmpty();
    }

    @Test
    void staffSeeTheFullProfileAndCandidatesOnlyTheirOwn() throws Exception {
        TestUser owner = candidates.verifiedCandidate();
        TestUser other = candidates.verifiedCandidate();
        JsonNode profile = candidates.updateProfile(owner, Map.of("fullName", "Dono Do Perfil", "phone", "923111222"));

        JsonNode staffView = json(mvc.perform(get("/api/v1/candidates/" + profile.path("id").asString() + "/profile")
                .with(users.bearer(users.create(UserRole.RECRUITER)))).andExpect(status().isOk()));
        assertThat(staffView.path("restricted").asBoolean()).isFalse();
        assertThat(staffView.path("candidate").path("phone").asString()).isEqualTo("+244923111222");
        assertThat(staffView.path("candidate").path("email").asString()).isEqualTo(owner.email());
        assertThat(staffView.path("applications")).isEmpty();
        assertThat(staffView.path("cv").isNull()).isTrue();

        // Another candidate only ever gets its own profile, whatever it sends
        JsonNode otherView = json(mvc.perform(get("/api/v1/me/candidate").param("id", profile.path("id").asString())
                .with(users.bearer(other))).andExpect(status().isOk()));
        assertThat(otherView.path("id").asString()).isNotEqualTo(profile.path("id").asString());
        mvc.perform(get("/api/v1/candidates/" + profile.path("id").asString() + "/profile").with(users.bearer(other)))
                .andExpect(status().isForbidden());

        Set<String> ids = Set.of(profile.path("id").asString(), otherView.path("id").asString());
        assertThat(ids.stream().collect(Collectors.toSet())).hasSize(2);
    }
}
