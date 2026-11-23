package com.minhaempresa.recrutamentoapi.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.RecordingSmsSender;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Stage 2 adjustments: rate limits, password confirmation, phone change and security alert emails. */
@IntegrationTest
class AccountSecurityIntegrationTest {

    /** "23/11/2026 às 09:30" in Angola time. */
    private static final String DATE_TIME = "\\d{2}/\\d{2}/\\d{4} às \\d{2}:\\d{2}";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    RecordingSmsSender sms;

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

    private static void assertAlert(Email email, String expectedSentence) {
        assertThat(email.subject()).isEqualTo("Alerta de segurança na sua conta FEH OIRAM");
        assertThat(email.text()).containsPattern(expectedSentence + " em " + DATE_TIME)
                .contains("Se não reconhece esta alteração, redefina já a sua senha")
                .contains("/recuperar-senha");
    }

    @Nested
    class RateLimits {

        @Test
        void forgotPasswordIsLimitedToThreePerEmailWhetherTheAccountExistsOrNot() throws Exception {
            TestUser user = users.create(UserRole.CANDIDATE);
            String unknown = TestUsers.uniqueEmail();
            for (int i = 0; i < 3; i++) {
                new AuthApi(mvc).postJson("/api/v1/auth/password/forgot", Map.of("email", user.email())).andExpect(status().isNoContent());
                new AuthApi(mvc).postJson("/api/v1/auth/password/forgot", Map.of("email", unknown)).andExpect(status().isNoContent());
            }

            // A different IP each time: the per-email limit still applies, with the same answer for both
            new AuthApi(mvc).postJson("/api/v1/auth/password/forgot", Map.of("email", user.email().toUpperCase()))
                    .andExpect(status().isTooManyRequests());
            new AuthApi(mvc).postJson("/api/v1/auth/password/forgot", Map.of("email", unknown))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void registrationIsLimitedPerEmailAndPerPhone() throws Exception {
            String email = TestUsers.uniqueEmail();
            new AuthApi(mvc).postJson("/api/v1/auth/register", registration(email, TestUsers.uniqueLocalPhone())).andExpect(status().isCreated());
            new AuthApi(mvc).postJson("/api/v1/auth/register", registration(email, TestUsers.uniqueLocalPhone())).andExpect(status().isConflict());
            new AuthApi(mvc).postJson("/api/v1/auth/register", registration(email, TestUsers.uniqueLocalPhone())).andExpect(status().isConflict());
            new AuthApi(mvc).postJson("/api/v1/auth/register", registration(email, TestUsers.uniqueLocalPhone())).andExpect(status().isTooManyRequests());

            String phone = TestUsers.uniqueLocalPhone();
            for (int i = 0; i < 3; i++) {
                new AuthApi(mvc).postJson("/api/v1/auth/register", registration(TestUsers.uniqueEmail(), phone));
            }
            new AuthApi(mvc).postJson("/api/v1/auth/register", registration(TestUsers.uniqueEmail(), phone)).andExpect(status().isTooManyRequests());
        }

        @Test
        void registrationIsLimitedPerIp() throws Exception {
            // Test profile: 5 per hour (production: 30, because of CGNAT)
            AuthApi sameIp = new AuthApi(mvc, TestUsers.fromIp("10.251.0.1"));
            for (int i = 0; i < 5; i++) {
                sameIp.postJson("/api/v1/auth/register", registration(TestUsers.uniqueEmail(), TestUsers.uniqueLocalPhone())).andExpect(status().isCreated());
            }

            sameIp.postJson("/api/v1/auth/register", registration(TestUsers.uniqueEmail(), TestUsers.uniqueLocalPhone())).andExpect(status().isTooManyRequests());
        }

        @Test
        void currentPasswordChecksAreLimitedPerUser() throws Exception {
            TestUser user = users.create(UserRole.RECRUITER);
            String token = users.accessToken(user);
            for (int i = 0; i < 10; i++) {
                api.sendJson("POST", "/api/v1/me/password", token, Map.of("current", "Errada12345", "next", "NovaSenha2026"))
                        .andExpect(status().isUnprocessableContent());
            }

            api.sendJson("POST", "/api/v1/me/password", token, Map.of("current", TestUsers.PASSWORD, "next", "NovaSenha2026"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    class PhoneChange {

        @Test
        void withoutTwoStepVerificationThePasswordIsEnough() throws Exception {
            TestUser user = users.create(UserRole.CANDIDATE);
            String token = users.accessToken(user);
            String newPhone = TestUsers.uniqueLocalPhone();

            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", newPhone, "currentPassword", "Errada12345"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", newPhone))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", newPhone, "currentPassword", TestUsers.PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("+244" + newPhone));

            assertAlert(mailpit.awaitEmailTo(user.email()), "O número de telemóvel da sua conta foi alterado");
        }

        @Test
        void sameOrTakenNumberIsRejected() throws Exception {
            TestUser user = users.create(UserRole.CANDIDATE);
            TestUser other = users.create(UserRole.CANDIDATE);
            String token = users.accessToken(user);

            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", user.phone(), "currentPassword", TestUsers.PASSWORD))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Este já é o seu número de telemóvel."));
            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", other.phone(), "currentPassword", TestUsers.PASSWORD))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].field").value("phone"));
        }

        @Test
        void withTwoStepVerificationTheNewNumberMustBeConfirmedByCode() throws Exception {
            TestUser user = users.create(UserRole.RECRUITER);
            var entity = userRepository.findById(user.id()).orElseThrow();
            entity.setTwoFactorEnabled(true);
            userRepository.save(entity);
            String token = users.accessToken(user);
            String newPhone = TestUsers.uniqueLocalPhone();

            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", newPhone, "currentPassword", TestUsers.PASSWORD))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("code"))
                    .andExpect(jsonPath("$.errors[0].message").value("Indique o código enviado por SMS para o novo número."));

            api.sendJson("POST", "/api/v1/me/phone/code", token, Map.of("phone", newPhone))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresInSeconds").value(300));
            String code = sms.awaitCode("+244" + newPhone);
            assertThat(sms.sentTo(user.phone())).as("code goes to the NEW number").isEmpty();

            // The code only works with the number it was sent to
            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", TestUsers.uniqueLocalPhone(), "code", code, "currentPassword", TestUsers.PASSWORD))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("code"));

            api.sendJson("PUT", "/api/v1/me/phone", token, Map.of("phone", newPhone, "code", code, "currentPassword", TestUsers.PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("+244" + newPhone));
        }
    }

    @Nested
    class SecurityAlerts {

        @Test
        void passwordChangeSendsAnAlert() throws Exception {
            TestUser user = users.create(UserRole.ADMIN);

            api.sendJson("POST", "/api/v1/me/password", users.accessToken(user), Map.of("current", TestUsers.PASSWORD, "next", "NovaSenha2026"))
                    .andExpect(status().isNoContent());

            Email alert = mailpit.awaitEmailTo(user.email());
            assertAlert(alert, "A senha da sua conta foi alterada");
            assertThat(alert.html()).contains("Redefinir senha");
        }

        @Test
        void passwordResetSendsAnAlert() throws Exception {
            TestUser user = users.create(UserRole.CANDIDATE);
            api.postJson("/api/v1/auth/password/forgot", Map.of("email", user.email()));
            String token = mailpit.awaitEmailTo(user.email(), 1).token();

            api.postJson("/api/v1/auth/password/reset", Map.of("token", token, "password", "Redefinida2026")).andExpect(status().isNoContent());

            assertAlert(mailpit.awaitEmailTo(user.email(), 2), "A senha da sua conta foi redefinida através do link de recuperação");
        }

        @Test
        void enablingAndDisablingTwoStepVerificationSendAlerts() throws Exception {
            TestUser user = users.create(UserRole.RECRUITER);
            String token = users.accessToken(user);
            api.sendJson("POST", "/api/v1/me/two-factor/code", token, null).andExpect(status().isOk());
            String code = sms.awaitCode(user.phone());

            api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", true, "code", code)).andExpect(status().isOk());
            assertAlert(mailpit.awaitEmailTo(user.email(), 1), "A verificação em 2 passos foi ativada na sua conta");

            api.sendJson("PUT", "/api/v1/me/two-factor", token, Map.of("enabled", false, "currentPassword", TestUsers.PASSWORD)).andExpect(status().isOk());
            assertAlert(mailpit.awaitEmailTo(user.email(), 2), "A verificação em 2 passos foi desativada na sua conta");
        }
    }
}
