package com.minhaempresa.recrutamentoapi.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Production defaults: SMS-code login off and SMS simulation not allowed (no real SMS provider yet).
 * Uses its own application context.
 */
@IntegrationTest
@TestPropertySource(properties = {"app.auth.otp-login-enabled=false", "app.notifications.allow-simulated-sms=false"})
class ProductionDefaultsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Test
    void smsLoginEndpointsAnswer404WhenDisabled() throws Exception {
        AuthApi api = new AuthApi(mvc);

        api.postJson("/api/v1/auth/otp/request", Map.of("phone", "923456789"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NotFoundError"));
        api.postJson("/api/v1/auth/otp/verify", Map.of("phone", "923456789", "code", "123456")).andExpect(status().isNotFound());
    }

    @Test
    void twoStepVerificationCannotBeEnabledWithoutARealSmsProvider() throws Exception {
        AuthApi api = new AuthApi(mvc);
        String token = users.accessToken(users.create(UserRole.RECRUITER));

        api.sendJson("POST", "/api/v1/me/two-factor/code", token, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("A verificação em 2 passos não está disponível de momento."));
        api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", true, "code", "123456")).andExpect(status().isUnprocessableContent());
    }
}
