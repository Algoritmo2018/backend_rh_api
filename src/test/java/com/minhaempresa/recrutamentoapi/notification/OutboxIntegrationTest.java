package com.minhaempresa.recrutamentoapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.notification.service.OutboxCleanup;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.EmailAccounts;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The outbox: retries with the controllable clock, failure (and the administrators' bell), recovery,
 * retention, and which existing emails use it (secret links never do).
 */
@IntegrationTest
class OutboxIntegrationTest {

    static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    EmailOutbox outbox;

    @Autowired
    OutboxCleanup cleanup;

    @Autowired
    MutableClock clock;

    TestUser admin;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void setUp() {
        admin = users.create(UserRole.ADMIN);
        // Known state: only this test's administrator gets the emails "to every administrator"
        users.deactivateOtherAdministrators(admin);
        asAdmin = users.bearer(admin);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM email_routes");
        clock.reset();
    }

    /** Recruitment emails (not used by any other test yet) go to a server that refuses connections. */
    void routeRecruitmentToADownServer() throws Exception {
        UUID down = EmailAccounts.create(mvc, asAdmin, EmailAccounts.body(EmailAccounts.uniqueName("Em baixo"), "localhost", EmailAccounts.refusedPort(), "rh@fehoiram.test"));
        EmailAccounts.route(mvc, asAdmin, "recruitment", down);
    }

    Map<String, Object> row(String recipient) {
        return jdbc.queryForMap("SELECT * FROM outbound_emails WHERE recipient = ?", recipient);
    }

    void awaitAttempts(String recipient, int attempts) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> ((Number) row(recipient).get("attempts")).intValue() >= attempts);
    }

    @Test
    void anUnreachableServerIsRetriedOnScheduleThenFailedAndTheAdministratorsAreTold() throws Exception {
        routeRecruitmentToADownServer();
        String recipient = TestUsers.uniqueEmail();
        outbox.enqueue(EmailPurpose.RECRUITMENT, recipient, "Convite para entrevista", "Texto", null);

        awaitAttempts(recipient, 1);
        Map<String, Object> first = row(recipient);
        assertThat(first.get("status")).isEqualTo("PENDING");
        assertThat(first.get("last_error")).asString().isNotBlank();
        Duration wait = Duration.between(clock.instant(), ((Timestamp) first.get("next_attempt_at")).toInstant());
        assertThat(wait).isBetween(Duration.ofSeconds(45), Duration.ofMinutes(1));

        for (int i = 0; i < RETRY_DELAYS.size(); i++) {
            // Not yet due: nothing happens
            assertThat(((Number) row(recipient).get("attempts")).intValue()).isEqualTo(i + 1);
            clock.advance(RETRY_DELAYS.get(i));
            awaitAttempts(recipient, i + 2);
        }

        Map<String, Object> failed = row(recipient);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("attempts")).isEqualTo(6);
        assertThat(failed.get("sent_at")).isNull();

        Map<String, Object> bell = jdbc.queryForMap("SELECT * FROM notifications WHERE user_id = ? AND type = 'EMAIL_FAILED' AND body LIKE ?", admin.id(), "%" + recipient + "%");
        assertThat(bell.get("title")).isEqualTo("Email não entregue");
        assertThat(bell.get("body")).asString().contains("Convite para entrevista");
        assertThat(bell.get("link")).isEqualTo("/app/configuracoes");

        // A failed email is never sent again
        clock.advance(Duration.ofDays(1));
        Thread.sleep(600);
        assertThat(row(recipient).get("attempts")).isEqualTo(6);
    }

    @Test
    void anEmailIsDeliveredOnceTheServerIsFixed() throws Exception {
        routeRecruitmentToADownServer();
        String recipient = TestUsers.uniqueEmail();
        outbox.enqueue(EmailPurpose.RECRUITMENT, recipient, "Proposta", "Texto da proposta", null);
        awaitAttempts(recipient, 1);

        // The administrator removes the broken account: the default server is used again
        EmailAccounts.route(mvc, asAdmin, "recruitment", null);
        clock.advance(Duration.ofMinutes(1));

        assertThat(mailpit.awaitEmailTo(recipient).subject()).isEqualTo("Proposta");
        Map<String, Object> sent = row(recipient);
        assertThat(sent.get("status")).isEqualTo("SENT");
        assertThat(sent.get("attempts")).isEqualTo(2);
        assertThat(sent.get("sent_at")).isNotNull();
        assertThat(sent.get("last_error")).isNull();
    }

    @Test
    void sentEmailsAreDeletedAfter30DaysAndFailedOnesAfter90() {
        Instant now = clock.instant();
        UUID oldSent = insert("SENT", now.minus(Duration.ofDays(31)));
        UUID recentSent = insert("SENT", now.minus(Duration.ofDays(29)));
        UUID oldFailed = insert("FAILED", now.minus(Duration.ofDays(91)));
        UUID recentFailed = insert("FAILED", now.minus(Duration.ofDays(60)));
        UUID oldPending = insert("PENDING", now.minus(Duration.ofDays(200)));

        cleanup.deleteExpired();

        assertThat(exists(oldSent)).isFalse();
        assertThat(exists(oldFailed)).isFalse();
        assertThat(exists(recentSent)).isTrue();
        assertThat(exists(recentFailed)).isTrue();
        assertThat(exists(oldPending)).isTrue();
        jdbc.update("DELETE FROM outbound_emails WHERE id IN (?, ?, ?)", recentSent, recentFailed, oldPending);
    }

    UUID insert(String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        // Far in the future, so the worker never picks the pending one
        jdbc.update("""
                INSERT INTO outbound_emails (id, purpose, recipient, subject, text_body, status, attempts, next_attempt_at, created_at)
                VALUES (?, 'SYSTEM', ?, 'Assunto', 'Texto', ?, 0, ?, ?)""",
                id, TestUsers.uniqueEmail(), status, Timestamp.from(Instant.now().plus(Duration.ofDays(3650))), Timestamp.from(createdAt));
        return id;
    }

    boolean exists(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM outbound_emails WHERE id = ?", Integer.class, id) > 0;
    }

    @Test
    void secretLinksAreSentDirectlyAndNeverStored() throws Exception {
        TestUser user = users.create(UserRole.CANDIDATE);

        new AuthApi(mvc).postJson("/api/v1/auth/password/forgot", Map.of("email", user.email())).andExpect(status().isNoContent());

        String token = mailpit.awaitEmailTo(user.email()).token();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbound_emails WHERE recipient = ? OR text_body LIKE ? OR html_body LIKE ?",
                Integer.class, user.email(), "%" + token + "%", "%" + token + "%")).isZero();
    }

    @Test
    void securityAlertsGoThroughTheOutboxAsSystemEmails() throws Exception {
        TestUser user = users.create(UserRole.CANDIDATE);
        AuthApi api = new AuthApi(mvc);
        String accessToken = api.login(user.email(), TestUsers.PASSWORD).accessToken();

        api.sendJson("POST", "/api/v1/me/password", accessToken, Map.of("current", TestUsers.PASSWORD, "next", "NovaSenha2026"))
                .andExpect(status().isNoContent());

        assertThat(mailpit.awaitEmailTo(user.email()).subject()).isEqualTo("Alerta de segurança na sua conta FEH OIRAM");
        await().atMost(Duration.ofSeconds(5)).until(() -> "SENT".equals(row(user.email()).get("status")));
        assertThat(row(user.email()).get("purpose")).isEqualTo("SYSTEM");
    }

    @Test
    void contactRequestsReachTheAdministratorsAsClientEmailsAndInTheBell() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", "general");
        body.put("name", "Joana Baptista");
        body.put("email", TestUsers.uniqueEmail());
        body.put("phone", "923 111 222");
        body.put("message", "Gostaria de saber mais sobre os vossos serviços.");

        mvc.perform(post("/api/v1/public/contact-requests").with(TestUsers.randomIp())
                        .contentType(MediaType.APPLICATION_JSON).content(EmailAccounts.JSON.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        mailpit.awaitEmailTo(admin.email());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT purpose FROM outbound_emails WHERE recipient = ?", admin.email());
        assertThat(rows).singleElement().satisfies(r -> assertThat(r.get("purpose")).isEqualTo("CLIENTS"));
        Map<String, Object> bell = jdbc.queryForMap("SELECT * FROM notifications WHERE user_id = ?", admin.id());
        assertThat(bell.get("type")).isEqualTo("CONTACT_REQUEST_RECEIVED");
        assertThat(bell.get("title")).isEqualTo("Nova mensagem de contacto");
        assertThat(bell.get("body")).isEqualTo("Joana Baptista enviou uma mensagem pelo site.");
        assertThat(bell.get("link")).isEqualTo("/app/clientes");
    }
}
