package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class LoginIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserRepository userRepository;

    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void correctPasswordStartsASession() throws Exception {
        TestUser user = users.create(UserRole.RECRUITER);

        Session session = new AuthApi(mvc).login(user.email(), TestUsers.PASSWORD);

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.body().path("twoFactorRequired").asBoolean()).isFalse();
        assertThat(session.body().path("user").path("email").asString()).isEqualTo(user.email());
        assertThat(session.body().path("user").path("role").asString()).isEqualTo("recruiter");
        assertThat(session.body().path("user").has("passwordHash")).isFalse();
        assertThat(session.body().path("expiresAt").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.body().has("refreshToken")).as("refresh token never in the body").isFalse();
    }

    @Test
    void refreshTokenGoesInAStrictHttpOnlyCookie() throws Exception {
        TestUser user = users.create(UserRole.CANDIDATE);

        Session session = new AuthApi(mvc).login(user.email(), TestUsers.PASSWORD);

        Cookie cookie = session.response().getCookie(AuthApi.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getMaxAge()).isBetween((int) Duration.ofDays(7).minusMinutes(1).toSeconds(), (int) Duration.ofDays(7).toSeconds());
    }

    @Test
    void accessTokenCarriesRoleSessionAndClientClaims() throws Exception {
        TestUser client = users.create(UserRole.CLIENT);

        Session session = new AuthApi(mvc).login(client.email(), TestUsers.PASSWORD);

        var claims = session.claims();
        assertThat(claims.path("sub").asString()).isEqualTo(client.id().toString());
        assertThat(claims.path("roles").get(0).asString()).isEqualTo("CLIENT");
        assertThat(claims.path("sid").asString()).isNotBlank();
        assertThat(claims.path("clientId").asString()).isNotBlank();
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        TestUser user = users.create(UserRole.ADMIN);
        AuthApi api = new AuthApi(mvc);

        String wrongPassword = api.loginRequest(user.email(), "Errada12345").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AuthError"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = api.loginRequest("ninguem@teste.ao", "Errada12345").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(messageOf(wrongPassword)).isEqualTo("Email ou senha incorretos.").isEqualTo(messageOf(unknownEmail));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        TestUser user = users.create(UserRole.RECRUITER);

        assertThat(new AuthApi(mvc).login("  " + user.email().toUpperCase() + " ", TestUsers.PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutes() throws Exception {
        TestUser user = users.create(UserRole.RECRUITER);
        AuthApi api = new AuthApi(mvc);
        for (int i = 0; i < 5; i++) {
            api.loginRequest(user.email(), "Errada12345").andExpect(status().isUnauthorized());
        }

        // Locked: even the right password gets the generic answer
        api.loginRequest(user.email(), TestUsers.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha incorretos."));
        assertThat(userRepository.findById(user.id()).orElseThrow().getLockedUntil()).isNotNull();

        clock.advance(Duration.ofMinutes(16));
        assertThat(api.login(user.email(), TestUsers.PASSWORD).status()).isEqualTo(200);
        assertThat(userRepository.findById(user.id()).orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        TestUser user = users.create(UserRole.RECRUITER);
        AuthApi api = new AuthApi(mvc);
        for (int i = 0; i < 4; i++) {
            api.loginRequest(user.email(), "Errada12345").andExpect(status().isUnauthorized());
        }
        assertThat(api.login(user.email(), TestUsers.PASSWORD).status()).isEqualTo(200);
        api.loginRequest(user.email(), "Errada12345").andExpect(status().isUnauthorized());

        assertThat(api.login(user.email(), TestUsers.PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void inactiveOrInvitedAccountsCannotLogIn() throws Exception {
        TestUser inactive = users.create(UserRole.INTERVIEWER);
        var entity = userRepository.findById(inactive.id()).orElseThrow();
        entity.setActive(false);
        userRepository.save(entity);
        TestUser invited = users.create(UserRole.RECRUITER, false);
        AuthApi api = new AuthApi(mvc);

        api.loginRequest(inactive.email(), TestUsers.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha incorretos."));
        api.loginRequest(invited.email(), TestUsers.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha incorretos."));
    }

    @Test
    void tooManyAttemptsFromOneIpAre429() throws Exception {
        AuthApi api = new AuthApi(mvc, TestUsers.fromIp("10.250.250.250"));
        for (int i = 0; i < 20; i++) {
            api.loginRequest("ninguem-" + i + "@teste.ao", "Errada12345").andExpect(status().isUnauthorized());
        }

        api.loginRequest("ninguem@teste.ao", "Errada12345")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("AuthError"))
                .andExpect(jsonPath("$.message").value("Muitas tentativas. Aguarde alguns minutos e tente novamente."))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        new AuthApi(mvc).postJson("/api/v1/auth/login", java.util.Map.of("email", "", "password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void meReturnsTheUserAndExpiredAccessTokensAreRejected() throws Exception {
        TestUser user = users.create(UserRole.ADMIN);
        AuthApi api = new AuthApi(mvc);
        Session session = api.login(user.email(), TestUsers.PASSWORD);

        api.me(session.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.id().toString()));

        clock.advance(Duration.ofMinutes(17));
        api.me(session.accessToken()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("AuthError"));
    }

    private static String messageOf(String json) {
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(json).path("message").asString();
    }
}
