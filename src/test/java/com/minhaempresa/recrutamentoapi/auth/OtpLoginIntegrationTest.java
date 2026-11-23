package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.RecordingSmsSender;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OtpLoginIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    RecordingSmsSender sms;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    TestUser candidate;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        candidate = users.create(UserRole.CANDIDATE);
    }

    private String local(TestUser user) {
        return user.phone().substring(4);
    }

    @Test
    void smsCodeLogsIn() throws Exception {
        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
        String code = sms.awaitCode(candidate.phone());
        assertThat(sms.sentTo(candidate.phone()).getFirst().textBody()).startsWith("FEH OIRAM: o seu código de acesso é");

        Session session = api.postForSession("/api/v1/auth/otp/verify", Map.of("phone", "+244 " + local(candidate), "code", code));

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.body().path("user").path("id").asString()).isEqualTo(candidate.id().toString());
        assertThat(session.refreshToken()).isNotBlank();
    }

    @Test
    void codeIsSingleUse() throws Exception {
        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate)));
        String code = sms.awaitCode(candidate.phone());

        assertThat(api.postForSession("/api/v1/auth/otp/verify", Map.of("phone", local(candidate), "code", code)).status()).isEqualTo(200);
        assertThat(api.postForSession("/api/v1/auth/otp/verify", Map.of("phone", local(candidate), "code", code)).status()).isEqualTo(401);
    }

    @Test
    void fiveWrongCodesSpendTheCode() throws Exception {
        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate)));
        String code = sms.awaitCode(candidate.phone());
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            api.postJson("/api/v1/auth/otp/verify", Map.of("phone", local(candidate), "code", wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Código inválido ou expirado."));
        }

        assertThat(api.postForSession("/api/v1/auth/otp/verify", Map.of("phone", local(candidate), "code", code)).status()).isEqualTo(401);
    }

    @Test
    void unknownPhoneGetsTheSameAnswerAndNoSms() throws Exception {
        String unknown = TestUsers.uniqueLocalPhone();

        String unknownBody = api.postJson("/api/v1/auth/otp/request", Map.of("phone", unknown)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String knownBody = api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownBody).isEqualTo(knownBody);
        sms.awaitCode(candidate.phone());
        assertThat(sms.sentTo("+244" + unknown)).isEmpty();
    }

    @Test
    void sameNumberMustWaitBeforeAskingAgain() throws Exception {
        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate))).andExpect(status().isOk());

        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("AuthError"));
    }

    @Test
    void onlyCandidatesCanUseTheSmsLoginByDefault() throws Exception {
        TestUser recruiter = users.create(UserRole.RECRUITER);

        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(recruiter))).andExpect(status().isOk());

        Integer codes = jdbc.queryForObject("SELECT count(*) FROM one_time_tokens WHERE user_id = ? AND type = 'LOGIN_OTP'", Integer.class, recruiter.id());
        assertThat(codes).isZero();
    }

    @Test
    void accountsWithTwoStepVerificationCannotUseTheSmsLogin() throws Exception {
        var entity = userRepository.findById(candidate.id()).orElseThrow();
        entity.setTwoFactorEnabled(true);
        userRepository.save(entity);

        api.postJson("/api/v1/auth/otp/request", Map.of("phone", local(candidate))).andExpect(status().isOk());

        // An SMS-only login would skip their password: no code is even created (checked synchronously)
        Integer codes = jdbc.queryForObject("SELECT count(*) FROM one_time_tokens WHERE user_id = ? AND type = 'LOGIN_OTP'", Integer.class, candidate.id());
        assertThat(codes).isZero();
    }
}
