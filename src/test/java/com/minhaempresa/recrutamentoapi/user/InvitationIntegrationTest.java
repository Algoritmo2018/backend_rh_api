package com.minhaempresa.recrutamentoapi.user;

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
                .andExpect(jsonPath("$.emailVerified").value(false));

        Email invitation = mailpit.awaitEmailTo(email);
        assertThat(invitation.subject()).isEqualTo("Convite para o portal de recrutamento FEH OIRAM");
        assertThat(invitation.text()).contains("/aceitar-convite?token=").contains("Olá Paulo,").contains("7 dias");

        Session session = api.postForSession("/api/v1/auth/invitations/accept", Map.of("token", invitation.token(), "password", "Convidado2026"));

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.body().path("user").path("emailVerified").asBoolean()).isTrue();
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

    @Test
    void bootstrapAdministratorWasCreatedWithoutPasswordAndInvited() {
        User bootstrap = userRepository.findByEmail(BOOTSTRAP_ADMIN).orElseThrow();

        assertThat(bootstrap.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(bootstrap.hasPassword()).isFalse();
        assertThat(mailpit.awaitEmailTo(BOOTSTRAP_ADMIN).subject()).isEqualTo("Convite para o portal de recrutamento FEH OIRAM");
    }

    @Test
    void bootstrapReinvitesOnStartupWhenTheInvitationExpired() throws Exception {
        // The runner only acts while no administrator has a password
        jdbc.update("DELETE FROM users WHERE role = 'ADMIN' AND password_hash IS NOT NULL");
        mailpit.awaitEmailTo(BOOTSTRAP_ADMIN);
        int before = mailpit.countTo(BOOTSTRAP_ADMIN);

        bootstrapRunner.run(null);
        assertThat(mailpit.countTo(BOOTSTRAP_ADMIN)).as("invitation still valid: nothing sent").isEqualTo(before);

        clock.advance(Duration.ofDays(8));
        bootstrapRunner.run(null);

        mailpit.awaitEmailTo(BOOTSTRAP_ADMIN, before + 1);
    }
}
