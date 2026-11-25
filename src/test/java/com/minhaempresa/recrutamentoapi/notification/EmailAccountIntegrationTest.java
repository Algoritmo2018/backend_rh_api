package com.minhaempresa.recrutamentoapi.notification;

import static com.minhaempresa.recrutamentoapi.support.EmailAccounts.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.TestcontainersConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.service.EmailOutbox;
import com.minhaempresa.recrutamentoapi.support.EmailAccounts;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Settings tab "Emails de envio": SMTP accounts, routes per purpose and the test email. */
@IntegrationTest
class EmailAccountIntegrationTest {

    static final String ACCOUNTS = "/api/v1/settings/email-accounts";

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

    @Value("${spring.mail.host}")
    String mailHost;

    @Value("${spring.mail.port}")
    int mailPort;

    TestUser admin;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void setUp() {
        admin = users.create(UserRole.ADMIN);
        asAdmin = users.bearer(admin);
    }

    /** Routes are global: never leave one behind for the other tests. */
    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM email_routes");
    }

    Map<String, Object> mailpitAccount(String fromAddress) {
        return EmailAccounts.body(EmailAccounts.uniqueName("Mailpit"), mailHost, mailPort, fromAddress);
    }

    ResultActions putAccount(UUID id, Map<String, Object> body) throws Exception {
        return mvc.perform(put(ACCOUNTS + "/{id}", id).with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    /** Queues an email of the purpose and returns it once Mailpit has it. */
    Email sendThroughOutbox(EmailPurpose purpose, String subject) {
        String recipient = TestUsers.uniqueEmail();
        outbox.enqueue(purpose, recipient, subject, "Corpo do email.", null);
        return mailpit.awaitEmailTo(recipient);
    }

    String storedPassword(UUID id) {
        return jdbc.queryForObject("SELECT password_encrypted FROM email_accounts WHERE id = ?", String.class, id);
    }

    @Test
    void thePasswordIsEncryptedWriteOnlyAndKeptWhenLeftBlank() throws Exception {
        Map<String, Object> body = mailpitAccount("rh@fehoiram.test");
        body.put("username", "rh@fehoiram.test");
        body.put("password", "Segredo-SMTP-2026");
        String created = mvc.perform(post(ACCOUNTS).with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordEncrypted").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("Segredo-SMTP-2026");
        UUID id = UUID.fromString(JSON.readTree(created).path("id").asString());
        String encrypted = storedPassword(id);
        assertThat(encrypted).startsWith("v1:").doesNotContain("Segredo");

        // Blank password: the stored one is kept
        body.put("password", "");
        body.put("port", 2525);
        putAccount(id, body).andExpect(status().isOk()).andExpect(jsonPath("$.hasPassword").value(true)).andExpect(jsonPath("$.port").value(2525));
        assertThat(storedPassword(id)).isEqualTo(encrypted);

        // A new password replaces it
        body.put("password", "Outra-Senha-2026");
        putAccount(id, body).andExpect(status().isOk());
        assertThat(storedPassword(id)).startsWith("v1:").isNotEqualTo(encrypted);

        String list = mvc.perform(get(ACCOUNTS).with(asAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains(id.toString()).doesNotContain("Segredo").doesNotContain("Outra-Senha").doesNotContain("v1:");

        // Without a user there is no authentication, so no password is kept
        body.remove("username");
        body.put("password", "");
        putAccount(id, body).andExpect(status().isOk()).andExpect(jsonPath("$.hasPassword").value(false));
        assertThat(storedPassword(id)).isNull();
    }

    @Test
    void onlyAdministratorsManageAccounts() throws Exception {
        TestUser recruiter = users.create(UserRole.RECRUITER);
        TestUser client = users.create(UserRole.CLIENT);

        mvc.perform(get(ACCOUNTS).with(users.bearer(recruiter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/email-routes").with(users.bearer(client))).andExpect(status().isForbidden());
        mvc.perform(post(ACCOUNTS + "/test").with(users.bearer(recruiter)).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("account", mailpitAccount("rh@fehoiram.test"))))).andExpect(status().isForbidden());
        mvc.perform(get(ACCOUNTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAccountsAreRefusedOnTheirFields() throws Exception {
        Map<String, Object> body = mailpitAccount("nao-e-email");
        body.put("port", 70000);
        body.put("host", "smtp.exemplo.ao\r\nX-Injected: 1");
        mvc.perform(post(ACCOUNTS).with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'port')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'host')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'fromAddress')]").exists());

        Map<String, Object> first = mailpitAccount("rh@fehoiram.test");
        EmailAccounts.create(mvc, asAdmin, first);
        mvc.perform(post(ACCOUNTS).with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void routesListEveryPurposeAndAnAccountInUseCannotBeDeleted() throws Exception {
        UUID id = EmailAccounts.create(mvc, asAdmin, mailpitAccount("rh@fehoiram.test"));
        EmailAccounts.route(mvc, asAdmin, "clients", id);

        mvc.perform(get("/api/v1/settings/email-routes").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.purpose == 'clients')].accountId").value(id.toString()))
                .andExpect(jsonPath("$[?(@.purpose == 'system')].accountId").value(org.hamcrest.Matchers.contains((Object) null)));

        mvc.perform(delete(ACCOUNTS + "/{id}", id).with(asAdmin)).andExpect(status().isUnprocessableContent());

        EmailAccounts.route(mvc, asAdmin, "clients", null);
        mvc.perform(delete(ACCOUNTS + "/{id}", id).with(asAdmin)).andExpect(status().isNoContent());
        mvc.perform(delete(ACCOUNTS + "/{id}", id).with(asAdmin)).andExpect(status().isNotFound());
    }

    @Test
    void accountChangesApplyToTheNextEmailWithoutRestartAndInactiveAccountsFallBackToTheDefault() throws Exception {
        Map<String, Object> body = mailpitAccount("rh@fehoiram.test");
        body.put("fromName", "Recrutamento FEH");
        body.put("replyTo", "respostas@fehoiram.test");
        UUID id = EmailAccounts.create(mvc, asAdmin, body);
        EmailAccounts.route(mvc, asAdmin, "recruitment", id);

        Map<String, List<String>> first = mailpit.headers(sendThroughOutbox(EmailPurpose.RECRUITMENT, "Primeiro").id());
        assertThat(first.get("From")).singleElement().asString().contains("rh@fehoiram.test").contains("Recrutamento FEH");
        assertThat(first.get("Reply-To")).singleElement().asString().contains("respostas@fehoiram.test");

        body.put("fromAddress", "vagas@fehoiram.test");
        body.remove("replyTo");
        putAccount(id, body).andExpect(status().isOk());
        Map<String, List<String>> second = mailpit.headers(sendThroughOutbox(EmailPurpose.RECRUITMENT, "Segundo").id());
        assertThat(second.get("From")).singleElement().asString().contains("vagas@fehoiram.test");
        assertThat(second).doesNotContainKey("Reply-To");

        // Other purposes keep the default server and sender
        Map<String, List<String>> system = mailpit.headers(sendThroughOutbox(EmailPurpose.SYSTEM, "Sistema").id());
        assertThat(system.get("From")).singleElement().asString().contains("nao-responder@fehoiram.test");

        body.put("active", false);
        putAccount(id, body).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        Map<String, List<String>> third = mailpit.headers(sendThroughOutbox(EmailPurpose.RECRUITMENT, "Terceiro").id());
        assertThat(third.get("From")).singleElement().asString().contains("nao-responder@fehoiram.test");
    }

    @Test
    void lineBreaksInTheSenderNameAndSubjectNeverAddHeaders() throws Exception {
        Map<String, Object> body = mailpitAccount("rh@fehoiram.test");
        body.put("fromName", "Ana\r\nBcc: x@evil.com");
        String created = mvc.perform(post(ACCOUNTS).with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromName").value("Ana Bcc: x@evil.com"))
                .andReturn().getResponse().getContentAsString();
        EmailAccounts.route(mvc, asAdmin, "recruitment", UUID.fromString(JSON.readTree(created).path("id").asString()));

        Email email = sendThroughOutbox(EmailPurpose.RECRUITMENT, "Olá Ana\r\nBcc: x@evil.com");

        Map<String, List<String>> headers = mailpit.headers(email.id());
        assertThat(headers).doesNotContainKey("Bcc");
        assertThat(headers.get("Subject")).singleElement().asString().doesNotContain("\r").doesNotContain("\n");
        assertThat(email.subject()).isEqualTo("Olá Ana Bcc: x@evil.com");
        assertThat(mailpit.countTo("x@evil.com")).isZero();
    }

    @Test
    void theTestEmailGoesOnlyToTheLoggedInAdministrator() throws Exception {
        Map<String, Object> account = mailpitAccount("rh@fehoiram.test");
        account.put("name", "Conta RH");

        mvc.perform(post(ACCOUNTS + "/test").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("account", account))))
                .andExpect(status().isNoContent());

        Email email = mailpit.awaitEmailTo(admin.email());
        assertThat(email.subject()).isEqualTo("Email de teste FEH OIRAM");
        assertThat(email.text()).contains("Conta RH");
        assertThat(mailpit.headers(email.id()).get("From")).singleElement().asString().contains("rh@fehoiram.test");
        // Nothing was saved by the test
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_accounts WHERE name = 'Conta RH'", Integer.class)).isZero();
    }

    @Test
    void theTestEmailOfASavedAccountUsesTheStoredPasswordWhenLeftBlank() throws Exception {
        try (GenericContainer<?> smtp = new GenericContainer<>(DockerImageName.parse(TestcontainersConfiguration.MAILPIT_IMAGE))
                .withExposedPorts(TestcontainersConfiguration.MAILPIT_SMTP_PORT, TestcontainersConfiguration.MAILPIT_HTTP_PORT)
                // This server only accepts this user and password (the shared Mailpit accepts anyone)
                .withEnv("MP_SMTP_AUTH", "rh:Senha-Certa-2026")
                .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "true")
                .waitingFor(Wait.forHttp("/api/v1/info").forPort(TestcontainersConfiguration.MAILPIT_HTTP_PORT))) {
            smtp.start();
            MailpitClient inbox = new MailpitClient("http://" + smtp.getHost() + ":" + smtp.getMappedPort(TestcontainersConfiguration.MAILPIT_HTTP_PORT));
            Map<String, Object> account = EmailAccounts.body(EmailAccounts.uniqueName("Com senha"), smtp.getHost(),
                    smtp.getMappedPort(TestcontainersConfiguration.MAILPIT_SMTP_PORT), "rh@fehoiram.test");
            account.put("username", "rh");
            account.put("password", "Senha-Certa-2026");
            UUID id = EmailAccounts.create(mvc, asAdmin, account);

            // The form comes back with the password blank: the stored one is used
            account.put("password", "");
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("accountId", id.toString());
            request.put("account", account);
            mvc.perform(post(ACCOUNTS + "/test").with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
            assertThat(inbox.awaitEmailTo(admin.email()).subject()).isEqualTo("Email de teste FEH OIRAM");

            // Without the saved account there is no password: refused by the server
            mvc.perform(post(ACCOUNTS + "/test").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of("account", account))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("O servidor recusou o utilizador ou a senha.")));

            // A password typed in the form replaces the stored one for the test (and is not saved)
            account.put("password", "Senha-Errada-2026");
            mvc.perform(post(ACCOUNTS + "/test").with(asAdmin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableContent());
            assertThat(inbox.countTo(admin.email())).isEqualTo(1);
            assertThat(storedPassword(id)).isNotNull();
        }
    }

    @Test
    void aFailedTestEmailGivesAReadableErrorWithoutNetworkDetails() throws Exception {
        int port = EmailAccounts.refusedPort();
        Map<String, Object> account = EmailAccounts.body("Conta em baixo", "localhost", port, "rh@fehoiram.test");

        String response = mvc.perform(post(ACCOUNTS + "/test").with(asAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("account", account))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Não foi possível ligar ao servidor SMTP. Verifique o servidor, a porta e a segurança."))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("localhost").doesNotContain(String.valueOf(port)).doesNotContain("Exception");
    }
}
