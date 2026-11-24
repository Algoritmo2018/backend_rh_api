package com.minhaempresa.recrutamentoapi.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Candidates with a confirmed account and a profile, for the candidate tests. */
public final class CandidateApi {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    private final TestUsers users;
    private final UserService userService;
    private final AuthApi api;

    public CandidateApi(MockMvc mvc, TestUsers users, UserService userService) {
        this.users = users;
        this.userService = userService;
        this.api = new AuthApi(mvc, TestUsers.randomIp());
    }

    /** A candidate account whose email is confirmed (so its profile exists). */
    public TestUser verifiedCandidate() {
        TestUser user = users.create(UserRole.CANDIDATE);
        userService.markEmailVerified(user.id());
        return user;
    }

    public ResultActions patchProfile(TestUser candidate, Map<String, Object> body) throws Exception {
        return api.sendJson("PATCH", "/api/v1/me/candidate", users.accessToken(candidate), body);
    }

    /** Updates the profile (must succeed) and returns it. */
    public JsonNode updateProfile(TestUser candidate, Map<String, Object> body) throws Exception {
        return json(patchProfile(candidate, body).andExpect(status().isOk()));
    }

    public JsonNode setConsent(TestUser candidate, String type, boolean granted) throws Exception {
        return json(api.sendJson("PUT", "/api/v1/me/consents/" + type, users.accessToken(candidate), Map.of("granted", granted))
                .andExpect(status().isOk()));
    }

    public AuthApi api() {
        return api;
    }

    public static JsonNode json(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isBlank() ? JSON.missingNode() : JSON.readTree(body);
    }
}
