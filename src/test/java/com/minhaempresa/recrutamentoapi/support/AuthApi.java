package com.minhaempresa.recrutamentoapi.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Small client for the auth endpoints used by the integration tests. */
public final class AuthApi {

    public static final String COOKIE = "refresh_token";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final RequestPostProcessor ip;

    public AuthApi(MockMvc mvc) {
        this(mvc, TestUsers.randomIp());
    }

    public AuthApi(MockMvc mvc, RequestPostProcessor ip) {
        this.mvc = mvc;
        this.ip = ip;
    }

    /** Result of a request that may start a session. */
    public record Session(int status, JsonNode body, String accessToken, String refreshToken, MockHttpServletResponse response) {

        public String bearer() {
            return "Bearer " + accessToken;
        }

        /** Claims of the access token (payload decoded, signature not checked). */
        public JsonNode claims() {
            String payload = accessToken.split("\\.")[1];
            return JSON.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
        }
    }

    public ResultActions loginRequest(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(ip).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    public Session login(String email, String password) throws Exception {
        return toSession(loginRequest(email, password));
    }

    public Session refresh(String refreshToken) throws Exception {
        var request = post("/api/v1/auth/refresh").with(ip);
        if (refreshToken != null) {
            request.cookie(new Cookie(COOKIE, refreshToken));
        }
        return toSession(mvc.perform(request));
    }

    public ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/logout").with(ip).cookie(new Cookie(COOKIE, refreshToken)));
    }

    public ResultActions me(String accessToken) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken));
    }

    public ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).with(ip).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    /** Authenticated JSON request ({@code method} = POST, PUT or PATCH). */
    public ResultActions sendJson(String method, String url, String accessToken, Object body) throws Exception {
        var request = request(HttpMethod.valueOf(method), url)
                .with(ip)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(JSON.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    public Session postForSession(String url, Object body) throws Exception {
        return toSession(postJson(url, body));
    }

    public static Session toSession(ResultActions actions) throws Exception {
        MockHttpServletResponse response = actions.andReturn().getResponse();
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = content.isBlank() ? JSON.missingNode() : JSON.readTree(content);
        Cookie cookie = response.getCookie(COOKIE);
        String refresh = cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
        String access = body.path("token").isString() ? body.path("token").asString() : null;
        return new Session(response.getStatus(), body, access, refresh, response);
    }
}
