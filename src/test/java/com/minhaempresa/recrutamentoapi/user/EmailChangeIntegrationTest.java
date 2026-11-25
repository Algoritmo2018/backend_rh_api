package com.minhaempresa.recrutamentoapi.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.service.ApplicantData;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Stage 7b: a candidate changes the login email through a link sent to the new address. */
@IntegrationTest
class EmailChangeIntegrationTest {

    private static final String CHANGE = "/api/v1/me/email/change";
    private static final String CONFIRM = "/api/v1/auth/email/change/confirm";
    private static final String CONFIRM_SUBJECT = "Confirme o seu novo email";
    private static final String NOTICE_SUBJECT = "Pedido de alteração do email da sua conta";
    private static final String IN_USE_SUBJECT = "Pedido de alteração de email";
    private static final String TOKEN_INVALID = "O link é inválido ou expirou. Peça um novo.";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    CandidateService candidateService;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    CandidateApi candidates;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        candidates = new CandidateApi(mvc, users, userService);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void fullFlowChangesAccountAndProfileKeepsTheCurrentSessionAndAlertsTheOldAddress() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        Session current = api.login(candidate.email(), TestUsers.PASSWORD);
        Session other = api.login(candidate.email(), TestUsers.PASSWORD);
        String newEmail = TestUsers.uniqueEmail();

        requestChange(current.accessToken(), newEmail.toUpperCase(), TestUsers.PASSWORD).andExpect(status().isNoContent());

        Email link = mailpit.awaitEmailTo(newEmail);
        assertThat(link.subject()).isEqualTo(CONFIRM_SUBJECT);
        assertThat(link.text()).contains("http://localhost:5173/software_rh/confirmar-novo-email?token=").contains("24 horas");
        Email notice = mailpit.awaitEmailTo(candidate.email());
        assertThat(notice.subject()).isEqualTo(NOTICE_SUBJECT);
        assertThat(notice.text()).doesNotContain(newEmail).doesNotContain("token=");
        assertThat(currentEmailOf(candidate)).as("nothing changes before the confirmation").isEqualTo(candidate.email());

        confirm(link.token(), current.refreshToken()).andExpect(status().isNoContent());

        api.me(current.accessToken())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.emailVerified").value(true));
        JsonNode profile = CandidateApi.json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(candidate))).andExpect(status().isOk()));
        assertThat(profile.path("email").asString()).isEqualTo(newEmail);

        assertThat(api.login(newEmail, TestUsers.PASSWORD).status()).isEqualTo(200);
        assertThat(api.login(candidate.email(), TestUsers.PASSWORD).status()).isEqualTo(401);
        assertThat(api.refresh(current.refreshToken()).status()).as("current session kept").isEqualTo(200);
        assertThat(api.refresh(other.refreshToken()).status()).as("other sessions ended").isEqualTo(401);

        Email alert = mailpit.awaitEmailTo(candidate.email(), 2);
        assertThat(alert.subject()).isEqualTo("Alerta de segurança na sua conta FEH OIRAM");
        assertThat(alert.text()).contains("O email da sua conta foi alterado em").contains("/contacto");
        assertThat(mailpit.countTo(newEmail)).as("no alert to the new address").isEqualTo(1);
    }

    @Test
    void withoutTheSessionCookieEverySessionEnds() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        Session session = api.login(candidate.email(), TestUsers.PASSWORD);
        String newEmail = TestUsers.uniqueEmail();

        requestChange(session.accessToken(), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());
        confirm(mailpit.awaitEmailTo(newEmail).token(), null).andExpect(status().isNoContent());

        assertThat(api.refresh(session.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void anotherAccountsCookieDoesNotKeepAnything() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        Session session = api.login(candidate.email(), TestUsers.PASSWORD);
        TestUser stranger = candidates.verifiedCandidate();
        Session strangerSession = api.login(stranger.email(), TestUsers.PASSWORD);
        String newEmail = TestUsers.uniqueEmail();

        requestChange(session.accessToken(), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());
        confirm(mailpit.awaitEmailTo(newEmail).token(), strangerSession.refreshToken()).andExpect(status().isNoContent());

        assertThat(api.refresh(session.refreshToken()).status()).isEqualTo(401);
        assertThat(api.refresh(strangerSession.refreshToken()).status()).as("the stranger is not touched").isEqualTo(200);
    }

    @Test
    void wrongPasswordIsRefusedAndNothingIsSent() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String newEmail = TestUsers.uniqueEmail();

        requestChange(users.accessToken(candidate), newEmail, "Errada12345")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("A senha atual está incorreta."));
        requestChange(users.accessToken(candidate), candidate.email(), TestUsers.PASSWORD)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("newEmail"));

        // A valid request afterwards: once its emails arrived, the refused one would have arrived too
        String other = TestUsers.uniqueEmail();
        requestChange(users.accessToken(candidate), other, TestUsers.PASSWORD).andExpect(status().isNoContent());
        mailpit.awaitEmailTo(other);
        assertThat(mailpit.countTo(newEmail)).isZero();
    }

    @Test
    void passwordAttemptsShareTheLimitOfTheOtherSensitiveChanges() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String token = users.accessToken(candidate);
        for (int i = 0; i < 10; i++) {
            requestChange(token, TestUsers.uniqueEmail(), "Errada12345").andExpect(status().isUnprocessableContent());
        }
        requestChange(token, TestUsers.uniqueEmail(), TestUsers.PASSWORD).andExpect(status().isTooManyRequests());
        api.sendJson("POST", "/api/v1/me/password", token, Map.of("current", TestUsers.PASSWORD, "next", "NovaSenha2026"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void addressOfAnotherAccountGetsTheSameAnswerAndNothingChanges() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        TestUser owner = users.create(UserRole.RECRUITER);

        requestChange(users.accessToken(candidate), owner.email(), TestUsers.PASSWORD).andExpect(status().isNoContent());

        Email inUse = mailpit.awaitEmailTo(owner.email());
        assertThat(inUse.subject()).isEqualTo(IN_USE_SUBJECT);
        assertThat(inUse.text()).contains("já está associado a uma conta").doesNotContain("token=").doesNotContain(candidate.email());
        assertThat(mailpit.awaitEmailTo(candidate.email()).subject()).as("the same notice as a free address").isEqualTo(NOTICE_SUBJECT);
        assertThat(currentEmailOf(candidate)).isEqualTo(candidate.email());
        assertThat(pendingEmailChangeTokens(candidate)).isZero();
    }

    @Test
    void addressOfACandidateWithoutAccountIsNeverMerged() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String applicantEmail = TestUsers.uniqueEmail();
        var applicant = candidateService.findOrCreateForApplication(
                new ApplicantData("Pessoa Sem Conta", applicantEmail, TestUsers.uniqueLocalPhone(), Province.LUANDA, CandidateSource.WHATSAPP));

        requestChange(users.accessToken(candidate), applicantEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());

        assertThat(mailpit.awaitEmailTo(applicantEmail).subject()).isEqualTo(IN_USE_SUBJECT);
        assertThat(currentEmailOf(candidate)).isEqualTo(candidate.email());
        assertThat(jdbc.queryForObject("SELECT user_id FROM candidates WHERE id = ?", java.util.UUID.class, applicant.candidateId())).isNull();
    }

    @Test
    void linkExpiresAfter24Hours() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String newEmail = TestUsers.uniqueEmail();
        requestChange(users.accessToken(candidate), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());
        String token = mailpit.awaitEmailTo(newEmail).token();

        clock.advance(Duration.ofHours(24).plusMinutes(1));

        confirm(token, null).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value(TOKEN_INVALID));
        assertThat(currentEmailOf(candidate)).isEqualTo(candidate.email());
    }

    @Test
    void linkCanOnlyBeUsedOnce() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String newEmail = TestUsers.uniqueEmail();
        requestChange(users.accessToken(candidate), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());
        String token = mailpit.awaitEmailTo(newEmail).token();

        confirm(token, null).andExpect(status().isNoContent());
        confirm(token, null).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value(TOKEN_INVALID));
    }

    @Test
    void aNewRequestInvalidatesThePreviousLink() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String first = TestUsers.uniqueEmail();
        String second = TestUsers.uniqueEmail();
        requestChange(users.accessToken(candidate), first, TestUsers.PASSWORD).andExpect(status().isNoContent());
        String firstToken = mailpit.awaitEmailTo(first).token();
        requestChange(users.accessToken(candidate), second, TestUsers.PASSWORD).andExpect(status().isNoContent());
        String secondToken = mailpit.awaitEmailTo(second).token();

        confirm(firstToken, null).andExpect(status().isUnprocessableContent());
        confirm(secondToken, null).andExpect(status().isNoContent());
        assertThat(currentEmailOf(candidate)).isEqualTo(second);
    }

    @Test
    void passwordResetCancelsAPendingChange() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        String newEmail = TestUsers.uniqueEmail();
        requestChange(users.accessToken(candidate), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());
        String changeToken = mailpit.awaitEmailTo(newEmail).token();
        mailpit.awaitEmailTo(candidate.email());

        api.postJson("/api/v1/auth/password/forgot", Map.of("email", candidate.email())).andExpect(status().isNoContent());
        Email reset = awaitSubject(candidate.email(), "Redefinir a sua senha");
        api.postJson("/api/v1/auth/password/reset", Map.of("token", reset.token(), "password", "Redefinida2026")).andExpect(status().isNoContent());

        confirm(changeToken, null).andExpect(status().isUnprocessableContent());
        assertThat(currentEmailOf(candidate)).isEqualTo(candidate.email());
    }

    @Test
    void linksSentToTheOldAddressStopWorking() throws Exception {
        TestUser candidate = candidates.verifiedCandidate();
        api.postJson("/api/v1/auth/password/forgot", Map.of("email", candidate.email())).andExpect(status().isNoContent());
        String resetToken = awaitSubject(candidate.email(), "Redefinir a sua senha").token();
        String newEmail = TestUsers.uniqueEmail();
        requestChange(users.accessToken(candidate), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());

        confirm(mailpit.awaitEmailTo(newEmail).token(), null).andExpect(status().isNoContent());

        api.postJson("/api/v1/auth/password/reset", Map.of("token", resetToken, "password", "Redefinida2026"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void anUnconfirmedAccountCanFixItsEmailAndGetsItsProfile() throws Exception {
        TestUser candidate = users.create(UserRole.CANDIDATE);
        mvc.perform(get("/api/v1/me/candidate").with(users.bearer(candidate))).andExpect(status().isConflict());
        String newEmail = TestUsers.uniqueEmail();

        requestChange(users.accessToken(candidate), newEmail, TestUsers.PASSWORD).andExpect(status().isNoContent());
        confirm(mailpit.awaitEmailTo(newEmail).token(), null).andExpect(status().isNoContent());

        JsonNode profile = CandidateApi.json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(candidate))).andExpect(status().isOk()));
        assertThat(profile.path("email").asString()).isEqualTo(newEmail);
    }

    @Test
    void staffCannotChangeTheirEmail() throws Exception {
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.RECRUITER, UserRole.INTERVIEWER, UserRole.CLIENT)) {
            TestUser staff = users.create(role);
            requestChange(users.accessToken(staff), TestUsers.uniqueEmail(), TestUsers.PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ForbiddenError"));
        }
    }

    @Test
    void requestNeedsLoginAndValidData() throws Exception {
        api.postJson(CHANGE, Map.of("newEmail", TestUsers.uniqueEmail(), "currentPassword", TestUsers.PASSWORD)).andExpect(status().isUnauthorized());
        TestUser candidate = candidates.verifiedCandidate();
        requestChange(users.accessToken(candidate), "nao-e-um-email", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        confirm("", null).andExpect(status().isBadRequest());
        confirm("inexistente", null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void twoConfirmationsForTheSameAddressAtTheSameTimeOnlyOneWins() throws Exception {
        TestUser first = candidates.verifiedCandidate();
        TestUser second = candidates.verifiedCandidate();
        String target = TestUsers.uniqueEmail();
        requestChange(users.accessToken(first), target, TestUsers.PASSWORD).andExpect(status().isNoContent());
        requestChange(users.accessToken(second), target, TestUsers.PASSWORD).andExpect(status().isNoContent());
        List<String> tokens = mailpit.awaitEmailsTo(target, 2).stream().map(Email::token).toList();

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<MockHttpServletResponse>> calls = new ArrayList<>();
        for (String token : tokens) {
            calls.add(() -> {
                start.await();
                return confirm(token, null).andReturn().getResponse();
            });
        }
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<MockHttpServletResponse>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get());
            }
        }

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(204, 422);
        MockHttpServletResponse loser = responses.stream().filter(r -> r.getStatus() == 422).findFirst().orElseThrow();
        assertThat(loser.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Este email já está associado a outra conta. Nenhuma alteração foi feita.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidates WHERE email = ?", Integer.class, target)).isEqualTo(1);
        // The loser kept its old email in both tables
        String loserOld = currentEmailOf(first).equals(target) ? second.email() : first.email();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidates WHERE email = ?", Integer.class, loserOld)).isEqualTo(1);
    }

    // ----- Helpers -----

    private ResultActions requestChange(String accessToken, String newEmail, String password) throws Exception {
        return api.sendJson("POST", CHANGE, accessToken, Map.of("newEmail", newEmail, "currentPassword", password));
    }

    private ResultActions confirm(String token, String refreshToken) throws Exception {
        var request = post(CONFIRM).with(TestUsers.randomIp()).contentType(MediaType.APPLICATION_JSON)
                .content(CandidateApi.JSON.writeValueAsString(Map.of("token", token)));
        if (refreshToken != null) {
            request.cookie(new Cookie(AuthApi.COOKIE, refreshToken));
        }
        return mvc.perform(request);
    }

    private String currentEmailOf(TestUser user) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, user.id());
    }

    private int pendingEmailChangeTokens(TestUser user) {
        return jdbc.queryForObject("SELECT count(*) FROM one_time_tokens WHERE user_id = ? AND type = 'EMAIL_CHANGE' AND used_at IS NULL",
                Integer.class, user.id());
    }

    /** The newest email with that subject (the outbox and the direct sender may deliver in any order). */
    private Email awaitSubject(String address, String subject) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .until(() -> mailpit.awaitEmailsTo(address, 1).stream().anyMatch(e -> e.subject().equals(subject)));
        return mailpit.awaitEmailsTo(address, 1).stream().filter(e -> e.subject().equals(subject)).findFirst().orElseThrow();
    }
}
