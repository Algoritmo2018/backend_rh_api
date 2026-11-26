package com.minhaempresa.recrutamentoapi.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import com.minhaempresa.recrutamentoapi.user.entity.User;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import com.minhaempresa.recrutamentoapi.user.service.AdminBootstrapRunner;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class InvitationIntegrationTest {

    /** Configured in application-test.yml (app.bootstrap.admin). */
    private static final String BOOTSTRAP_ADMIN = "bootstrap-admin@fehoiram.test";

    private static final String INVITATION_SUBJECT = "Convite para o portal de recrutamento FEH OIRAM";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    MutableClock clock;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AdminBootstrapRunner bootstrapRunner;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    TestUser admin;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        admin = users.create(UserRole.ADMIN);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void invitedUserSetsThePasswordFromTheEmailAndIsLoggedIn() throws Exception {
        String email = TestUsers.uniqueEmail();
        api.sendJson("POST", "/api/v1/users", users.accessToken(admin), Map.of(
                        "name", "Paulo Lukamba", "email", email, "phone", TestUsers.uniqueLocalPhone(), "role", "recruiter", "jobTitle", "Recrutador"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("recruiter"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.invitationPending").value(true));

        Email invitation = mailpit.awaitEmailTo(email);
        assertThat(invitation.subject()).isEqualTo("Convite para o portal de recrutamento FEH OIRAM");
        assertThat(invitation.text()).contains("/aceitar-convite?token=").contains("Olá Paulo,").contains("7 dias");

        Session session = api.postForSession("/api/v1/auth/invitations/accept", Map.of("token", invitation.token(), "password", "Convidado2026"));

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.body().path("user").path("emailVerified").asBoolean()).isTrue();
        assertThat(session.body().path("user").path("invitationPending").asBoolean()).isFalse();
        assertThat(api.login(email, "Convidado2026").status()).isEqualTo(200);
        assertThat(api.postForSession("/api/v1/auth/invitations/accept", Map.of("token", invitation.token(), "password", "Outra2026x")).status())
                .as("single use").isEqualTo(422);
    }

    @Test
    void invitationCanBeResentUntilAccepted() throws Exception {
        TestUser invited = users.create(UserRole.INTERVIEWER, false);

        api.sendJson("POST", "/api/v1/users/" + invited.id() + "/invitation", users.accessToken(admin), null).andExpect(status().isNoContent());
        mailpit.awaitEmailTo(invited.email());

        TestUser active = users.create(UserRole.INTERVIEWER);
        api.sendJson("POST", "/api/v1/users/" + active.id() + "/invitation", users.accessToken(admin), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Este utilizador já ativou a conta."));
    }

    /*
     * The bootstrap administrator is active without a password, so it also receives the emails "to every
     * administrator" sent by other test classes, and some of them empty Mailpit. These tests never read its newest
     * email nor count all its emails (that depended on the test order): they check the invitation token in the
     * database and count only the invitation emails.
     */

    @Test
    void bootstrapAdministratorWasCreatedWithoutPasswordAndInvited() {
        User bootstrap = userRepository.findByEmail(BOOTSTRAP_ADMIN).orElseThrow();

        assertThat(bootstrap.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(bootstrap.hasPassword()).isFalse();
        assertThat(invitationTokens(bootstrap)).as("invited at startup").isPositive();
    }

    @Test
    void bootstrapReinvitesOnStartupWhenTheInvitationExpired() throws Exception {
        User bootstrap = userRepository.findByEmail(BOOTSTRAP_ADMIN).orElseThrow();
        // The runner only acts while no administrator has a password. The administrators of other tests are kept
        // (they may be referenced by their records) without password and inactive (no emails "to every administrator").
        jdbc.update("UPDATE users SET password_hash = NULL, active = FALSE WHERE role = 'ADMIN' AND password_hash IS NOT NULL");
        // A valid invitation (the one of the startup, or of an earlier run of this test)
        bootstrapRunner.run(null);
        int tokens = invitationTokens(bootstrap);
        int emails = mailpit.emailsTo(BOOTSTRAP_ADMIN, INVITATION_SUBJECT).size();

        bootstrapRunner.run(null);
        assertThat(invitationTokens(bootstrap)).as("invitation still valid: nothing sent").isEqualTo(tokens);

        clock.advance(Duration.ofDays(8));
        bootstrapRunner.run(null);

        assertThat(invitationTokens(bootstrap)).isEqualTo(tokens + 1);
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .until(() -> mailpit.emailsTo(BOOTSTRAP_ADMIN, INVITATION_SUBJECT).size() > emails);
        assertThat(mailpit.emailsTo(BOOTSTRAP_ADMIN, INVITATION_SUBJECT).getFirst().text()).contains("/aceitar-convite?token=");
    }

    private int invitationTokens(User user) {
        return jdbc.queryForObject("SELECT count(*) FROM one_time_tokens WHERE user_id = ? AND type = 'INVITATION'", Integer.class, user.getId());
    }
}
