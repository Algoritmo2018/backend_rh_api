package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
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
class PasswordResetIntegrationTest {

    private static final String NEW_PASSWORD = "Redefinida2026";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    MutableClock clock;

    AuthApi api;
    TestUser user;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        user = users.create(UserRole.CANDIDATE);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void resetLinkIsEmailedAndSetsANewPassword() throws Exception {
        Session oldSession = api.login(user.email(), TestUsers.PASSWORD);
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email())).andExpect(status().isNoContent());

        Email email = mailpit.awaitEmailTo(user.email());
        assertThat(email.subject()).isEqualTo("Redefinir a sua senha");
        assertThat(email.text()).contains("http://localhost:5173/software_rh/redefinir-senha?token=");
        assertThat(email.html()).contains("Redefinir senha").contains("/redefinir-senha?token=");

        api.postJson("/api/v1/auth/password/reset", Map.of("token", email.token(), "password", NEW_PASSWORD)).andExpect(status().isNoContent());

        assertThat(api.login(user.email(), NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(api.login(user.email(), TestUsers.PASSWORD).status()).isEqualTo(401);
        assertThat(api.refresh(oldSession.refreshToken()).status()).as("existing sessions revoked").isEqualTo(401);
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        String unknown = TestUsers.uniqueEmail();

        api.postJson("/api/v1/auth/password/forgot", Map.of("email", unknown)).andExpect(status().isNoContent());
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email())).andExpect(status().isNoContent());

        // Once the email of the real account arrived, the other one would have arrived too
        mailpit.awaitEmailTo(user.email());
        assertThat(mailpit.countTo(unknown)).isZero();
    }

    @Test
    void resetLinkCanOnlyBeUsedOnce() throws Exception {
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email()));
        String token = mailpit.awaitEmailTo(user.email()).token();

        api.postJson("/api/v1/auth/password/reset", Map.of("token", token, "password", NEW_PASSWORD)).andExpect(status().isNoContent());
        api.postJson("/api/v1/auth/password/reset", Map.of("token", token, "password", "OutraSenha2026"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("ValidationError"))
                .andExpect(jsonPath("$.message").value("O link é inválido ou expirou. Peça um novo."));
    }

    @Test
    void resetLinkExpiresAfterOneHour() throws Exception {
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email()));
        String token = mailpit.awaitEmailTo(user.email()).token();

        clock.advance(Duration.ofMinutes(61));

        api.postJson("/api/v1/auth/password/reset", Map.of("token", token, "password", NEW_PASSWORD)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aNewRequestInvalidatesThePreviousLink() throws Exception {
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email()));
        String first = mailpit.awaitEmailTo(user.email(), 1).token();
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email()));
        String second = mailpit.awaitEmailTo(user.email(), 2).token();

        api.postJson("/api/v1/auth/password/reset", Map.of("token", first, "password", NEW_PASSWORD)).andExpect(status().isUnprocessableContent());
        api.postJson("/api/v1/auth/password/reset", Map.of("token", second, "password", NEW_PASSWORD)).andExpect(status().isNoContent());
    }

    @Test
    void resetLiftsALockout() throws Exception {
        for (int i = 0; i < 5; i++) {
            api.loginRequest(user.email(), "Errada12345");
        }
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email()));
        String token = mailpit.awaitEmailTo(user.email()).token();

        api.postJson("/api/v1/auth/password/reset", Map.of("token", token, "password", NEW_PASSWORD)).andExpect(status().isNoContent());

        assertThat(api.login(user.email(), NEW_PASSWORD).status()).isEqualTo(200);
    }
}
