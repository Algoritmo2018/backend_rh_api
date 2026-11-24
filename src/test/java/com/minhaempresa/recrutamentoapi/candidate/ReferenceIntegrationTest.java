package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class ReferenceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    static Map<String, Object> reference(String candidateId, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("candidateId", candidateId);
        body.put("name", name);
        body.put("company", "Seguradora Palanca");
        body.put("role", "Diretora de RH");
        body.put("relationship", "Antiga chefia");
        body.put("phone", "923 111 444");
        body.put("email", "Chefe@Palanca.AO");
        return body;
    }

    @Test
    void recruitersRegisterAndCheckReferences() throws Exception {
        CandidateApi candidates = new CandidateApi(mvc, users, userService);
        TestUser owner = candidates.verifiedCandidate();
        String candidateId = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("id").asString();
        TestUser recruiter = users.create(UserRole.RECRUITER);
        String token = users.accessToken(recruiter);

        JsonNode first = json(candidates.api().sendJson("POST", "/api/v1/references", token, reference(candidateId, "Maria Fernanda Lopes"))
                .andExpect(status().isCreated()));
        assertThat(first.path("status").asString()).isEqualTo("pending");
        assertThat(first.path("phone").asString()).isEqualTo("+244923111444");
        assertThat(first.path("email").asString()).isEqualTo("chefe@palanca.ao");
        assertThat(first.path("checkedByName").isNull()).isTrue();
        Map<String, Object> withoutEmail = reference(candidateId, "João Manuel Neto");
        withoutEmail.put("email", "");
        JsonNode second = json(candidates.api().sendJson("POST", "/api/v1/references", token, withoutEmail).andExpect(status().isCreated()));
        assertThat(second.path("email").isNull()).isTrue();

        JsonNode verified = json(candidates.api().sendJson("PATCH", "/api/v1/references/" + first.path("id").asString() + "/status", token,
                Map.of("status", "verified", "notes", "Confirmou 4 anos na empresa.")).andExpect(status().isOk()));
        assertThat(verified.path("checkedById").asString()).isEqualTo(recruiter.id().toString());
        assertThat(verified.path("checkedByName").asString()).startsWith("Utilizador Teste");
        assertThat(verified.path("checkedAt").isNull()).isFalse();
        JsonNode pending = json(candidates.api().sendJson("PATCH", "/api/v1/references/" + first.path("id").asString() + "/status", token,
                Map.of("status", "pending")).andExpect(status().isOk()));
        assertThat(pending.path("checkedById").isNull()).isTrue();

        JsonNode list = json(mvc.perform(get("/api/v1/candidates/" + candidateId + "/references").with(users.bearer(recruiter))).andExpect(status().isOk()));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("id").asString()).isEqualTo(second.path("id").asString());
    }

    @Test
    void rulesOfTheReferenceForm() throws Exception {
        CandidateApi candidates = new CandidateApi(mvc, users, userService);
        TestUser owner = candidates.verifiedCandidate();
        String candidateId = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(owner)))).path("id").asString();
        String token = users.accessToken(users.create(UserRole.ADMIN));

        Map<String, Object> withApplication = reference(candidateId, "Maria Fernanda Lopes");
        withApplication.put("applicationId", UUID.randomUUID().toString());
        candidates.api().sendJson("POST", "/api/v1/references", token, withApplication).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("applicationId"));
        candidates.api().sendJson("POST", "/api/v1/references", token, reference(UUID.randomUUID().toString(), "Maria Fernanda Lopes"))
                .andExpect(status().isNotFound());
        Map<String, Object> invalid = reference(candidateId, "Maria");
        invalid.put("phone", "123");
        invalid.put("relationship", "");
        JsonNode error = json(candidates.api().sendJson("POST", "/api/v1/references", token, invalid).andExpect(status().isBadRequest()));
        assertThat(error.path("errors").toString()).contains("name").contains("phone").contains("relationship");
        candidates.api().sendJson("PATCH", "/api/v1/references/" + UUID.randomUUID() + "/status", token, Map.of("status", "verified"))
                .andExpect(status().isNotFound());
    }
}
