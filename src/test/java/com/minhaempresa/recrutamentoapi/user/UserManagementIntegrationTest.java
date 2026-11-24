package com.minhaempresa.recrutamentoapi.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.AuthApi.Session;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class UserManagementIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    AuthApi api;
    TestUser admin;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        admin = users.create(UserRole.ADMIN);
    }

    private Map<String, Object> newUser(String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Inês Sapalo");
        body.put("email", TestUsers.uniqueEmail());
        body.put("phone", TestUsers.uniqueLocalPhone());
        body.put("role", role);
        body.put("jobTitle", "Psicóloga Organizacional");
        return body;
    }

    @Nested
    class Permissions {

        @ParameterizedTest(name = "{0}: GET /users → {1}, GET /users/internal → {2}, POST /users → {3}")
        @CsvSource({
            "ADMIN,       200, 200, 201",
            "RECRUITER,   403, 200, 403",
            "INTERVIEWER, 403, 200, 403",
            "CLIENT,      403, 403, 403",
            "CANDIDATE,   403, 403, 403",
        })
        void matrix(UserRole role, int list, int internal, int create) throws Exception {
            TestUser user = role == UserRole.ADMIN ? admin : users.create(role);
            String token = users.accessToken(user);

            assertThat(mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus()).isEqualTo(list);
            assertThat(mvc.perform(get("/api/v1/users/internal").header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus()).isEqualTo(internal);
            assertThat(api.sendJson("POST", "/api/v1/users", token, newUser("recruiter")).andReturn().getResponse().getStatus()).isEqualTo(create);
        }

        @Test
        void forbiddenAnswersUseTheStandardFormat() throws Exception {
            String token = users.accessToken(users.create(UserRole.RECRUITER));

            api.sendJson("PATCH", "/api/v1/users/" + admin.id() + "/status", token, Map.of("active", false))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ForbiddenError"))
                    .andExpect(jsonPath("$.message").value("Não tem permissão para realizar esta ação."));
        }

        @Test
        void anonymousRequestsAre401() throws Exception {
            mvc.perform(get("/api/v1/users/internal")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Lists {

        @Test
        void userListExcludesCandidatesByDefaultAndFiltersByRole() throws Exception {
            TestUser candidate = users.create(UserRole.CANDIDATE);
            TestUser interviewer = users.create(UserRole.INTERVIEWER);
            String token = users.accessToken(admin);

            String all = mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
            assertThat(all).doesNotContain(candidate.email()).contains(interviewer.email());

            mvc.perform(get("/api/v1/users").param("roles", "interviewer").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].role").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("interviewer"))));
        }

        @Test
        void internalListHasNoContactDetails() throws Exception {
            users.create(UserRole.INTERVIEWER);

            mvc.perform(get("/api/v1/users/internal").header("Authorization", "Bearer " + users.accessToken(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").exists())
                    .andExpect(jsonPath("$[0].name").exists())
                    .andExpect(jsonPath("$[0].role").exists())
                    .andExpect(jsonPath("$[0].email").doesNotExist())
                    .andExpect(jsonPath("$[0].phone").doesNotExist());
        }
    }

    @Nested
    class Rules {

        @Test
        void duplicateEmailIs409() throws Exception {
            TestUser existing = users.create(UserRole.RECRUITER);
            Map<String, Object> body = newUser("recruiter");
            body.put("email", existing.email().toUpperCase());

            api.sendJson("POST", "/api/v1/users", users.accessToken(admin), body)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].field").value("email"));
        }

        @Test
        void candidatesAreNotManagedHere() throws Exception {
            api.sendJson("POST", "/api/v1/users", users.accessToken(admin), newUser("candidate"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Os candidatos criam a sua própria conta no portal."));
        }

        @Test
        void clientUsersNeedACompany() throws Exception {
            api.sendJson("POST", "/api/v1/users", users.accessToken(admin), newUser("client"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("clientId"))
                    .andExpect(jsonPath("$.errors[0].message").value("Escolha a empresa cliente."));

            Map<String, Object> withClient = newUser("client");
            withClient.put("clientId", users.createClient().toString());
            api.sendJson("POST", "/api/v1/users", users.accessToken(admin), withClient)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clientId").value(withClient.get("clientId")));
        }

        @Test
        void clientUsersNeedAnExistingActiveCompany() throws Exception {
            for (String clientId : new String[] {UUID.randomUUID().toString(), users.createClient("Empresa Inativa", false).toString()}) {
                Map<String, Object> body = newUser("client");
                body.put("clientId", clientId);
                api.sendJson("POST", "/api/v1/users", users.accessToken(admin), body)
                        .andExpect(status().isUnprocessableContent())
                        .andExpect(jsonPath("$.errors[0].field").value("clientId"))
                        .andExpect(jsonPath("$.errors[0].message").value("Escolha uma empresa cliente ativa."));
            }

            // Moving an existing client user to an inactive company is refused too
            TestUser clientUser = users.create(UserRole.CLIENT);
            Map<String, Object> move = newUser("client");
            move.put("email", clientUser.email());
            move.put("clientId", users.createClient("Outra Inativa", false).toString());
            api.sendJson("PUT", "/api/v1/users/" + clientUser.id(), users.accessToken(admin), move)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("clientId"));
        }

        @Test
        void emailCannotBeChanged() throws Exception {
            TestUser recruiter = users.create(UserRole.RECRUITER);
            Map<String, Object> body = newUser("recruiter");
            body.put("email", "outro@teste.ao");

            api.sendJson("PUT", "/api/v1/users/" + recruiter.id(), users.accessToken(admin), body)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("O email não pode ser alterado."));
        }

        @Test
        void adminCannotChangeOwnRoleNorDeactivateThemselves() throws Exception {
            Map<String, Object> body = newUser("recruiter");
            body.put("email", admin.email());
            String token = users.accessToken(admin);

            api.sendJson("PUT", "/api/v1/users/" + admin.id(), token, body)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Não pode alterar o seu próprio papel."));
            api.sendJson("PATCH", "/api/v1/users/" + admin.id() + "/status", token, Map.of("active", false))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.message").value("Não pode desativar a sua própria conta."));
        }

        @Test
        void deactivationEndsSessionsAtOnceButTheAccessTokenLivesUntilItExpires() throws Exception {
            TestUser recruiter = users.create(UserRole.RECRUITER);
            Session session = api.login(recruiter.email(), TestUsers.PASSWORD);

            api.sendJson("PATCH", "/api/v1/users/" + recruiter.id() + "/status", users.accessToken(admin), Map.of("active", false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            assertThat(api.refresh(session.refreshToken()).status()).isEqualTo(401);
            assertThat(api.login(recruiter.email(), TestUsers.PASSWORD).status()).isEqualTo(401);
            // Documented limitation: the short-lived access token stays valid until it expires (15 min)
            api.me(session.accessToken()).andExpect(status().isOk());
        }

        @Test
        void roleChangeEndsSessionsSoTheNextLoginCarriesTheNewRole() throws Exception {
            TestUser recruiter = users.create(UserRole.RECRUITER);
            Session session = api.login(recruiter.email(), TestUsers.PASSWORD);
            Map<String, Object> body = newUser("interviewer");
            body.put("email", recruiter.email());

            api.sendJson("PUT", "/api/v1/users/" + recruiter.id(), users.accessToken(admin), body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("interviewer"));

            assertThat(api.refresh(session.refreshToken()).status()).isEqualTo(401);
            assertThat(api.login(recruiter.email(), TestUsers.PASSWORD).claims().path("roles").get(0).asString()).isEqualTo("INTERVIEWER");
        }

        @Test
        void ownProfileUpdateChangesNameAndJobTitleButNotThePhone() throws Exception {
            TestUser recruiter = users.create(UserRole.RECRUITER);

            api.sendJson("PATCH", "/api/v1/me", users.accessToken(recruiter),
                            Map.of("name", "Josefa Nzinga", "phone", TestUsers.uniqueLocalPhone(), "jobTitle", "Recrutadora Sénior"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Josefa Nzinga"))
                    .andExpect(jsonPath("$.jobTitle").value("Recrutadora Sénior"))
                    .andExpect(jsonPath("$.phone").value(recruiter.phone()))
                    .andExpect(jsonPath("$.email").value(recruiter.email()));
        }
    }
}
