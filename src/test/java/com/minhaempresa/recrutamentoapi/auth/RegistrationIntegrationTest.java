package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RegistrationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    UserRepository userRepository;

    AuthApi api;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
    }

    private static Map<String, Object> registration(String email, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Ana Paula Mbinda");
        body.put("email", email);
        body.put("phone", phone);
        body.put("password", "Candidata2026");
        body.put("acceptTerms", true);
        return body;
    }

    @Test
    void candidateRegistersIsLoggedInAndConfirmsTheEmail() throws Exception {
        String email = TestUsers.uniqueEmail();
        String localPhone = TestUsers.uniqueLocalPhone();

        Session session = AuthApi.toSession(api.postJson("/api/v1/auth/register", registration(email, localPhone)).andExpect(status().isCreated()));

        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.body().path("user").path("role").asString()).isEqualTo("candidate");
        assertThat(session.body().path("user").path("phone").asString()).as("stored in E.164").isEqualTo("+244" + localPhone);
        assertThat(session.body().path("user").path("emailVerified").asBoolean()).isFalse();
        assertThat(userRepository.findByEmail(email).orElseThrow().getTermsAcceptedAt()).isNotNull();

        Email confirmation = mailpit.awaitEmailTo(email);
        assertThat(confirmation.subject()).isEqualTo("Confirme o seu email");
        assertThat(confirmation.text()).contains("/confirmar-email?token=").contains("Olá Ana,");
        api.postJson("/api/v1/auth/email/verify", Map.of("token", confirmation.token())).andExpect(status().isNoContent());

        api.me(session.accessToken()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void duplicateEmailIs409WithTheFrontendMessage() throws Exception {
        String email = TestUsers.uniqueEmail();
        api.postJson("/api/v1/auth/register", registration(email, TestUsers.uniqueLocalPhone())).andExpect(status().isCreated());

        api.postJson("/api/v1/auth/register", registration(email.toUpperCase(), TestUsers.uniqueLocalPhone()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ValidationError"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.message").value("Já existe uma conta com este email. Entre ou recupere a senha."));
    }

    @Test
    void termsMustBeAccepted() throws Exception {
        Map<String, Object> body = registration(TestUsers.uniqueEmail(), TestUsers.uniqueLocalPhone());
        body.put("acceptTerms", false);

        api.postJson("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("acceptTerms"))
                .andExpect(jsonPath("$.errors[0].message").value("Tem de aceitar a Política de Privacidade para criar conta."));
    }

    @Test
    void frontendValidationRulesAreEnforced() throws Exception {
        Map<String, Object> body = registration("nao-e-email", "812345678");
        body.put("name", "Ana");
        body.put("password", "curta1");

        api.postJson("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
    }

    @Test
    void confirmationLinkCannotBeReused() throws Exception {
        String email = TestUsers.uniqueEmail();
        api.postJson("/api/v1/auth/register", registration(email, TestUsers.uniqueLocalPhone()));
        String token = mailpit.awaitEmailTo(email).token();

        api.postJson("/api/v1/auth/email/verify", Map.of("token", token)).andExpect(status().isNoContent());
        api.postJson("/api/v1/auth/email/verify", Map.of("token", token)).andExpect(status().isUnprocessableContent());
    }
}
