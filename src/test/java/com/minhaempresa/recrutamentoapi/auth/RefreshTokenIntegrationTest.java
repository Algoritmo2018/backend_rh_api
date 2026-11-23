package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RefreshTokenIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    MutableClock clock;

    AuthApi api;
    TestUser user;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        user = users.create(UserRole.RECRUITER);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheSession() throws Exception {
        Session login = api.login(user.email(), TestUsers.PASSWORD);

        Session refreshed = api.refresh(login.refreshToken());

        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(login.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.body().path("user").path("id").asString()).isEqualTo(user.id().toString());
        assertThat(refreshed.claims().path("sid").asString()).as("same session id").isEqualTo(login.claims().path("sid").asString());
        assertThat(api.refresh(refreshed.refreshToken()).status()).isEqualTo(200);
    }

    @Test
    void reusingARotatedTokenWithinTheGracePeriodIsRejectedWithoutEndingTheSession() throws Exception {
        Session login = api.login(user.email(), TestUsers.PASSWORD);
        Session refreshed = api.refresh(login.refreshToken());

        // Second tab refreshing with the old token a few seconds later
        clock.advance(Duration.ofSeconds(5));
        assertThat(api.refresh(login.refreshToken()).status()).isEqualTo(401);

        assertThat(api.refresh(refreshed.refreshToken()).status()).as("the new token still works").isEqualTo(200);
    }

    @Test
    void reusingARotatedTokenAfterTheGracePeriodRevokesTheWholeSession() throws Exception {
        Session login = api.login(user.email(), TestUsers.PASSWORD);
        Session refreshed = api.refresh(login.refreshToken());

        clock.advance(Duration.ofSeconds(21));
        Session reuse = api.refresh(login.refreshToken());

        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.body().path("message").asString()).isEqualTo("A sua sessão expirou. Inicie sessão novamente.");
        assertThat(api.refresh(refreshed.refreshToken()).status()).as("family revoked").isEqualTo(401);
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Session login = api.login(user.email(), TestUsers.PASSWORD);

        clock.advance(Duration.ofDays(7).plusMinutes(1));

        assertThat(api.refresh(login.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void sessionEndsAfterThirtyDaysEvenWhenRefreshedRegularly() throws Exception {
        String token = api.login(user.email(), TestUsers.PASSWORD).refreshToken();
        for (int day = 6; day <= 24; day += 6) {
            clock.advance(Duration.ofDays(6));
            Session refreshed = api.refresh(token);
            assertThat(refreshed.status()).as("refresh on day %d", day).isEqualTo(200);
            token = refreshed.refreshToken();
        }
        // Day 30 + 1 minute: the last token would still be valid for days, but the family is too old
        clock.advance(Duration.ofDays(6).plusMinutes(1));

        assertThat(api.refresh(token).status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Session login = api.login(user.email(), TestUsers.PASSWORD);

        var logout = api.logout(login.refreshToken()).andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(logout.getCookie(AuthApi.COOKIE).getMaxAge()).isZero();
        assertThat(api.refresh(login.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        assertThat(api.refresh(null).status()).isEqualTo(401);
        assertThat(api.refresh("not-a-real-token").status()).isEqualTo(401);
    }

    @Test
    void otherSessionsOfTheSameUserAreIndependent() throws Exception {
        Session laptop = api.login(user.email(), TestUsers.PASSWORD);
        Session phone = api.login(user.email(), TestUsers.PASSWORD);

        api.logout(laptop.refreshToken());

        assertThat(api.refresh(phone.refreshToken()).status()).isEqualTo(200);
    }

    @Test
    void changingThePasswordKeepsOnlyTheCurrentSession() throws Exception {
        Session current = api.login(user.email(), TestUsers.PASSWORD);
        Session other = api.login(user.email(), TestUsers.PASSWORD);

        api.sendJson("POST", "/api/v1/me/password", current.accessToken(), Map.of("current", TestUsers.PASSWORD, "next", "NovaSenha2026"))
                .andExpect(status().isNoContent());

        assertThat(api.refresh(other.refreshToken()).status()).as("other session revoked").isEqualTo(401);
        assertThat(api.refresh(current.refreshToken()).status()).as("current session kept").isEqualTo(200);
        assertThat(api.login(user.email(), "NovaSenha2026").status()).isEqualTo(200);
    }

    @Test
    void wrongCurrentPasswordIs422OnThatField() throws Exception {
        Session session = api.login(user.email(), TestUsers.PASSWORD);

        api.sendJson("POST", "/api/v1/me/password", session.accessToken(), Map.of("current", "Errada12345", "next", "NovaSenha2026"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("current"))
                .andExpect(jsonPath("$.errors[0].message").value("A senha atual está incorreta."));
    }

    @Test
    void weakNewPasswordIs400() throws Exception {
        Session session = api.login(user.email(), TestUsers.PASSWORD);

        api.sendJson("POST", "/api/v1/me/password", session.accessToken(), Map.of("current", TestUsers.PASSWORD, "next", "semnumeros"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("A senha deve ter entre 8 e 72 caracteres, com letras e números."));
    }
}
