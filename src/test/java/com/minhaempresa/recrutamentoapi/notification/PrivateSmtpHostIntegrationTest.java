package com.minhaempresa.recrutamentoapi.notification;

import static com.minhaempresa.recrutamentoapi.support.EmailAccounts.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.support.EmailAccounts;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Production setting {@code app.mail.block-private-hosts=true}: SMTP hosts on internal networks are
 * refused on save, on the test email and again before connecting.
 */
@IntegrationTest
@TestPropertySource(properties = "app.mail.block-private-hosts=true")
class PrivateSmtpHostIntegrationTest {

    static final String ACCOUNTS = "/api/v1/settings/email-accounts";
    static final String NOT_ALLOWED = "Este servidor não é permitido. Use um servidor SMTP público.";

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

    @Value("${spring.mail.port}")
    int mailPort;

    RequestPostProcessor asAdmin;

    @BeforeEach
    void setUp() {
        TestUser admin = users.create(UserRole.ADMIN);
        asAdmin = users.bearer(admin);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM email_routes");
    }

    ResultActions post(String url, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "localhost", "10.0.0.5", "192.168.1.10", "169.254.169.254"})
    void internalHostsAreRefusedOnSave(String host) throws Exception {
        String name = EmailAccounts.uniqueName("Interna");
        post(ACCOUNTS, EmailAccounts.body(name, host, 25, "rh@fehoiram.test"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("host"))
                .andExpect(jsonPath("$.errors[0].message").value(NOT_ALLOWED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_accounts WHERE name = ?", Integer.class, name)).isZero();
    }

    @Test
    void theTestEmailRefusesInternalHostsWithoutConnecting() throws Exception {
        String response = post(ACCOUNTS + "/test", Map.of("account", EmailAccounts.body("Teste", "127.0.0.1", mailPort, "rh@fehoiram.test")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("host"))
                .andReturn().getResponse().getContentAsString();
        // The answer never says what the host resolved to
        assertThat(response).doesNotContain("127.0.0.1").doesNotContain(String.valueOf(mailPort));
    }

    @Test
    void anAccountThatNowPointsInsideIsRefusedBeforeConnecting() throws Exception {
        // Saved while the name was public; later it resolves to an internal address (DNS rebinding).
        // Simulated by writing the internal host directly: the API would refuse it.
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO email_accounts (id, name, host, port, security, from_address, active, created_at, updated_at)
                VALUES (?, ?, 'localhost', ?, 'NONE', 'rh@fehoiram.test', TRUE, now(), now())""",
                id, EmailAccounts.uniqueName("Religada"), mailPort);
        jdbc.update("INSERT INTO email_routes (id, purpose, account_id, created_at, updated_at) VALUES (?, 'RECRUITMENT', ?, now(), now())",
                UUID.randomUUID(), id);
        String recipient = TestUsers.uniqueEmail();

        outbox.enqueue(EmailPurpose.RECRUITMENT, recipient, "Entrevista", "Texto", null);

        await().atMost(Duration.ofSeconds(15)).until(() ->
                jdbc.queryForObject("SELECT attempts FROM outbound_emails WHERE recipient = ?", Integer.class, recipient) >= 1);
        Map<String, Object> row = jdbc.queryForMap("SELECT status, last_error FROM outbound_emails WHERE recipient = ?", recipient);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("last_error")).asString().contains("BusinessException");
        // Mailpit (on localhost) was never reached
        assertThat(mailpit.countTo(recipient)).isZero();
    }

    @Test
    void publicHostsAreAccepted() throws Exception {
        post(ACCOUNTS, EmailAccounts.body(EmailAccounts.uniqueName("Pública"), "8.8.8.8", 587, "rh@fehoiram.test"))
                .andExpect(status().isCreated());
    }
}
