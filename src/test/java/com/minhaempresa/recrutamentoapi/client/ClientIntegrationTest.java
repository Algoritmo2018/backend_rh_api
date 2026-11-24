package com.minhaempresa.recrutamentoapi.client;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class ClientIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi api;
    TestUser admin;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, TestUsers.randomIp());
        admin = users.create(UserRole.ADMIN);
    }

    static Map<String, Object> contact(String id, String name, boolean primary) {
        Map<String, Object> contact = new HashMap<>();
        contact.put("id", id);
        contact.put("name", name);
        contact.put("role", "Diretora de RH");
        contact.put("email", "Contacto." + name.split(" ")[0] + "@Empresa.ao");
        contact.put("phone", "923 456 789");
        contact.put("isPrimary", primary);
        return contact;
    }

    /** Body as the frontend sends it (including the ignored feePercentage). */
    static Map<String, Object> newClient(String nif, List<Map<String, Object>> contacts) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Petróleos do Kwanza " + UUID.randomUUID().toString().substring(0, 4));
        body.put("sector", "oil_gas");
        body.put("nif", nif);
        body.put("province", "Luanda");
        body.put("city", "Luanda");
        body.put("address", "Rua Major Kanhangulo, 100");
        body.put("website", "");
        body.put("feePercentage", 12);
        body.put("status", "active");
        body.put("notes", "");
        body.put("contacts", contacts);
        return body;
    }

    ResultActions createClient(Map<String, Object> body) throws Exception {
        return api.sendJson("POST", "/api/v1/clients", users.accessToken(admin), body);
    }

    JsonNode create(Map<String, Object> body) throws Exception {
        return read(createClient(body).andExpect(status().isCreated()));
    }

    ResultActions patch(String id, Map<String, Object> body) throws Exception {
        return api.sendJson("PATCH", "/api/v1/clients/" + id, users.accessToken(admin), body);
    }

    static JsonNode read(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    @Nested
    class Creation {

        @Test
        void createsTheClientWithItsPrimaryContactAndNoFees() throws Exception {
            JsonNode client = create(newClient(TestUsers.uniqueNif(), List.of(contact("cc-1", "Ana Paula Silva", true))));

            assertThat(client.path("status").asString()).isEqualTo("active");
            assertThat(client.path("sector").asString()).isEqualTo("oil_gas");
            assertThat(client.path("province").asString()).isEqualTo("Luanda");
            assertThat(client.path("website").isNull()).isTrue();
            assertThat(client.has("feePercentage")).isFalse();
            assertThat(client.has("feesTotal")).isFalse();
            assertThat(client.path("openJobs").asLong()).isZero();
            assertThat(client.path("totalJobs").asLong()).isZero();
            assertThat(client.path("hires").asLong()).isZero();
            JsonNode contact = client.path("contacts").get(0);
            assertThat(UUID.fromString(contact.path("id").asString())).isNotNull();
            assertThat(contact.path("isPrimary").asBoolean()).isTrue();
            assertThat(contact.path("email").asString()).isEqualTo("contacto.ana@empresa.ao");
            assertThat(contact.path("phone").asString()).isEqualTo("+244923456789");

            // Author audit
            UUID createdBy = jdbc.queryForObject("SELECT created_by FROM clients WHERE id = ?::uuid", UUID.class, client.path("id").asString());
            assertThat(createdBy).isEqualTo(admin.id());
            assertThat(jdbc.queryForObject("SELECT created_by FROM client_contacts WHERE client_id = ?::uuid", UUID.class, client.path("id").asString()))
                    .isEqualTo(admin.id());
        }

        @Test
        void aNewClientNeedsAContact() throws Exception {
            createClient(newClient(TestUsers.uniqueNif(), List.of()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("contacts"))
                    .andExpect(jsonPath("$.errors[0].message").value("Indique o contacto principal."));
        }

        @ParameterizedTest
        @ValueSource(strings = {"123", "12345678", "12345678901234X", "123456789012345", "12345 6789"})
        void nifHasNineToFourteenDigits(String nif) throws Exception {
            createClient(newClient(nif, List.of(contact(null, "Ana Paula Silva", true))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("nif"))
                    .andExpect(jsonPath("$.errors[0].message").value("O NIF tem 9 a 14 dígitos."));
        }

        @Test
        void nifIsUniqueAmongClients() throws Exception {
            String nif = TestUsers.uniqueNif();
            JsonNode first = create(newClient(nif, List.of(contact(null, "Ana Paula Silva", true))));

            createClient(newClient(nif, List.of(contact(null, "Rui Costa Neto", true))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].field").value("nif"))
                    .andExpect(jsonPath("$.errors[0].message").value("Já existe um cliente com este NIF."));

            JsonNode second = create(newClient(TestUsers.uniqueNif(), List.of(contact(null, "Rui Costa Neto", true))));
            patch(second.path("id").asString(), Map.of("nif", nif)).andExpect(status().isConflict());
            // Sending its own NIF again is fine
            patch(first.path("id").asString(), Map.of("nif", nif)).andExpect(status().isOk());
        }

        @Test
        void contactRulesFollowTheFrontend() throws Exception {
            Map<String, Object> badContact = contact(null, "Al", true);
            badContact.put("role", "X");
            badContact.put("email", "invalido");
            badContact.put("phone", "123");
            createClient(newClient(TestUsers.uniqueNif(), List.of(badContact)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.length()").value(4));

            createClient(newClient(TestUsers.uniqueNif(), List.of(contact(null, "Ana Paula Silva", true), contact(null, "Rui Costa Neto", true))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].message").value("Só pode haver um contacto principal."));
        }
    }

    @Nested
    class Update {

        @Test
        void patchChangesOnlyTheFieldsSent() throws Exception {
            JsonNode client = create(newClient(TestUsers.uniqueNif(), List.of(contact(null, "Ana Paula Silva", true))));
            String id = client.path("id").asString();

            JsonNode updated = read(patch(id, Map.of("city", "Benguela", "province", "Benguela", "website", "https://kwanza.ao")).andExpect(status().isOk()));

            assertThat(updated.path("city").asString()).isEqualTo("Benguela");
            assertThat(updated.path("province").asString()).isEqualTo("Benguela");
            assertThat(updated.path("website").asString()).isEqualTo("https://kwanza.ao");
            assertThat(updated.path("name").asString()).isEqualTo(client.path("name").asString());
            assertThat(updated.path("contacts")).isEqualTo(client.path("contacts"));
            mvc.perform(get("/api/v1/clients/" + id).with(users.bearer(admin))).andExpect(jsonPath("$.city").value("Benguela"));

            patch(id, Map.of("website", "kwanza.ao")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].message").value("Use um endereço completo, ex.: https://empresa.ao"));
        }

        @Test
        void contactsFollowTheChildCollectionRule() throws Exception {
            JsonNode other = create(newClient(TestUsers.uniqueNif(), List.of(contact(null, "Outra Pessoa Silva", true))));
            String otherContactId = other.path("contacts").get(0).path("id").asString();
            JsonNode client = create(newClient(TestUsers.uniqueNif(), List.of(contact("cc-1", "Ana Paula Silva", true), contact("cc-2", "Bruno Lopes Dias", false))));
            String id = client.path("id").asString();
            String anaId = client.path("contacts").get(0).path("id").asString();

            Map<String, Object> ana = contact(anaId, "Ana Paula Silva Editada", false);
            Map<String, Object> carla = contact("cc-new-3", "Carla Mendes Neto", true);
            Map<String, Object> stolen = contact(otherContactId, "Daniel Sousa Pinto", false);
            JsonNode updated = read(patch(id, Map.of("contacts", List.of(ana, carla, stolen))).andExpect(status().isOk()));

            JsonNode contacts = updated.path("contacts");
            assertThat(contacts.size()).isEqualTo(3);
            List<String> names = new ArrayList<>();
            contacts.forEach(c -> names.add(c.path("name").asString()));
            // Bruno (missing from the list) was deleted
            assertThat(names).containsExactlyInAnyOrder("Ana Paula Silva Editada", "Carla Mendes Neto", "Daniel Sousa Pinto");
            for (JsonNode c : contacts) {
                switch (c.path("name").asString()) {
                    case "Ana Paula Silva Editada" -> {
                        assertThat(c.path("id").asString()).isEqualTo(anaId);
                        assertThat(c.path("isPrimary").asBoolean()).isFalse();
                    }
                    case "Carla Mendes Neto" -> assertThat(c.path("isPrimary").asBoolean()).isTrue();
                    default -> assertThat(c.path("id").asString()).isNotEqualTo(otherContactId);
                }
            }
            // The other client's contact was not touched
            assertThat(jdbc.queryForObject("SELECT name FROM client_contacts WHERE id = ?::uuid", String.class, otherContactId)).isEqualTo("Outra Pessoa Silva");
            assertThat(jdbc.queryForObject("SELECT client_id FROM client_contacts WHERE id = ?::uuid", UUID.class, otherContactId))
                    .isEqualTo(UUID.fromString(other.path("id").asString()));

            // No contact marked: the first becomes the primary one
            JsonNode again = read(patch(id, Map.of("contacts", List.of(contact(anaId, "Ana Paula Silva", false), contact(null, "Eva Gomes Lima", false)))).andExpect(status().isOk()));
            assertThat(again.path("contacts").size()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT name FROM client_contacts WHERE client_id = ?::uuid AND is_primary", String.class, id)).isEqualTo("Ana Paula Silva");

            // All contacts may be removed from an existing client
            JsonNode none = read(patch(id, Map.of("contacts", List.of())).andExpect(status().isOk()));
            assertThat(none.path("contacts").size()).isZero();
        }
    }

    @Nested
    class Permissions {

        @Test
        void recruitersOnlyGetTheMinimalList() throws Exception {
            create(newClient(TestUsers.uniqueNif(), List.of(contact(null, "Ana Paula Silva", true))));
            TestUser recruiter = users.create(UserRole.RECRUITER);

            mvc.perform(get("/api/v1/clients").with(users.bearer(recruiter))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/clients/" + users.createClient()).with(users.bearer(recruiter))).andExpect(status().isForbidden());
            api.sendJson("POST", "/api/v1/clients", users.accessToken(recruiter), newClient(TestUsers.uniqueNif(), List.of(contact(null, "Ana Paula Silva", true))))
                    .andExpect(status().isForbidden());

            JsonNode options = read(mvc.perform(get("/api/v1/clients/options").with(users.bearer(recruiter))).andExpect(status().isOk()));
            assertThat(options.size()).isPositive();
            JsonNode option = options.get(0);
            assertThat(option.has("id")).isTrue();
            assertThat(option.has("name")).isTrue();
            assertThat(option.has("sector")).isTrue();
            assertThat(option.has("status")).isTrue();
            assertThat(option.has("contacts")).isFalse();
            assertThat(option.has("nif")).isFalse();
        }

        @Test
        void interviewersAndClientsHaveNoAccess() throws Exception {
            for (UserRole role : List.of(UserRole.INTERVIEWER, UserRole.CLIENT, UserRole.CANDIDATE)) {
                TestUser user = users.create(role);
                mvc.perform(get("/api/v1/clients").with(users.bearer(user))).andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/clients/options").with(users.bearer(user))).andExpect(status().isForbidden());
            }
        }

        @Test
        void unknownClientIs404() throws Exception {
            mvc.perform(get("/api/v1/clients/" + UUID.randomUUID()).with(users.bearer(admin)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Cliente não encontrado."));
        }
    }

    @Nested
    class Deactivation {

        @Test
        void deactivatingTheClientEndsTheAccessOfItsUsersAndReactivatingGivesItBack() throws Exception {
            UUID clientId = users.createClient();
            TestUser portalUser = users.createClientUser(clientId);
            TestUser blockedUser = users.createClientUser(clientId);
            jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", blockedUser.id());
            Session session = api.login(portalUser.email(), TestUsers.PASSWORD);
            assertThat(session.status()).isEqualTo(200);

            patch(clientId.toString(), Map.of("status", "inactive")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("inactive"));

            // Sessions ended at once; no login while the company is inactive (same answer as a wrong password)
            assertThat(api.refresh(session.refreshToken()).status()).isEqualTo(401);
            api.loginRequest(portalUser.email(), TestUsers.PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Email ou senha incorretos."));
            // The access token issued before still works for a few minutes, but the portal refuses it
            mvc.perform(get("/api/v1/client-portal/job-requests").header("Authorization", session.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("A sua empresa está inativa. Contacte a FEH OIRAM."));
            // The users keep their own state
            assertThat(jdbc.queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, portalUser.id())).isTrue();

            patch(clientId.toString(), Map.of("status", "active")).andExpect(status().isOk());

            Session again = api.login(portalUser.email(), TestUsers.PASSWORD);
            assertThat(again.status()).isEqualTo(200);
            mvc.perform(get("/api/v1/client-portal/job-requests").header("Authorization", again.bearer())).andExpect(status().isOk());
            // A user deactivated individually stays out
            assertThat(api.login(blockedUser.email(), TestUsers.PASSWORD).status()).isEqualTo(401);
        }
    }
}
