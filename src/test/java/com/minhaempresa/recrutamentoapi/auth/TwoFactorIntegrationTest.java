package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.RecordingSmsSender;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class TwoFactorIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    RecordingSmsSender sms;

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

    /** Enables two-step verification through the API; returns the number of SMS sent so far. */
    private int enable() throws Exception {
        String token = users.accessToken(user);
        api.sendJson("POST", "/api/v1/me/two-factor/code", token, null).andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(300));
        String code = sms.awaitCode(user.phone(), 1);
        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", true, "code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
        return 1;
    }

    @Test
    void enablingRequiresTheSmsCode() throws Exception {
        String token = users.accessToken(user);
        api.sendJson("POST", "/api/v1/me/two-factor/code", token, null).andExpect(status().isOk());
        String code = sms.awaitCode(user.phone());
        String wrong = code.equals("000000") ? "111111" : "000000";

        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", true, "code", wrong))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("Código inválido ou expirado."));
        Map<String, Object> withoutCode = new HashMap<>();
        withoutCode.put("enabled", true);
        api.sendJson("PUT", "/api/v1/me/two-factor", token, withoutCode).andExpect(status().isUnprocessableContent());

        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", true, "code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));
    }

    @Test
    void loginAsksForTheSecondStepAndTheSmsCodeCompletesIt() throws Exception {
        int sent = enable();

        Session first = api.login(user.email(), TestUsers.PASSWORD);

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("twoFactorRequired").asBoolean()).isTrue();
        assertThat(first.body().path("challengeExpiresInSeconds").asLong()).isEqualTo(300);
        assertThat(first.accessToken()).as("no session yet").isNull();
        assertThat(first.refreshToken()).isNull();

        String code = sms.awaitCode(user.phone(), sent + 1);
        Session session = api.postForSession("/api/v1/auth/login/two-factor",
                Map.of("challengeToken", first.body().path("challengeToken").asString(), "code", code));

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
    }

    @Test
    void challengeAllowsFiveAttempts() throws Exception {
        int sent = enable();
        Session first = api.login(user.email(), TestUsers.PASSWORD);
        String challenge = first.body().path("challengeToken").asString();
        String code = sms.awaitCode(user.phone(), sent + 1);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(api.postForSession("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", wrong)).status()).isEqualTo(401);
        }

        assertThat(api.postForSession("/api/v1/auth/login/two-factor", Map.of("challengeToken", challenge, "code", code)).status())
                .as("challenge spent: log in again").isEqualTo(401);
    }

    @Test
    void challengeExpiresAfterFiveMinutes() throws Exception {
        int sent = enable();
        Session first = api.login(user.email(), TestUsers.PASSWORD);
        String code = sms.awaitCode(user.phone(), sent + 1);

        clock.advance(Duration.ofMinutes(6));

        assertThat(api.postForSession("/api/v1/auth/login/two-factor",
                Map.of("challengeToken", first.body().path("challengeToken").asString(), "code", code)).status()).isEqualTo(401);
    }

    @Test
    void disablingRequiresTheCurrentPassword() throws Exception {
        enable();
        String token = users.accessToken(user);

        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("Indique a sua senha atual."));
        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", false, "currentPassword", "Errada12345"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("A senha atual está incorreta."));

        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", false, "currentPassword", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false));
        assertThat(api.login(user.email(), TestUsers.PASSWORD).accessToken()).isNotBlank();
    }
}
