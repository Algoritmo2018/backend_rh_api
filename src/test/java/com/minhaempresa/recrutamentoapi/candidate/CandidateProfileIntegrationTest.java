package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.service.ApplicantData;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateService.ApplicantLink;
import com.minhaempresa.recrutamentoapi.shared.domain.CandidateSource;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class CandidateProfileIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    CandidateService candidateService;

    @Autowired
    JdbcTemplate jdbc;

    CandidateApi candidates;

    @BeforeEach
    void setUp() {
        candidates = new CandidateApi(mvc, users, userService);
    }

    static Map<String, Object> experience(String id, String company, String role, String start, String end, boolean current) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("company", company);
        item.put("role", role);
        item.put("province", "Luanda");
        item.put("startDate", start);
        item.put("endDate", end);
        item.put("current", current);
        item.put("description", "Gestão de armazém e inventários.");
        return item;
    }

    static Map<String, Object> education(String id, String level, int start, Integer end) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("institution", "Universidade Agostinho Neto");
        item.put("degree", "Engenharia Informática");
        item.put("level", level);
        item.put("field", "Informática");
        item.put("startYear", start);
        item.put("endYear", end);
        return item;
    }

    static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    @Nested
    class AccountLink {

        @Test
        void theProfileOnlyExistsAfterTheEmailIsConfirmed() throws Exception {
            TestUser user = users.create(UserRole.CANDIDATE);

            mvc.perform(get("/api/v1/me/candidate").with(users.bearer(user))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Confirme o seu email para criar o seu perfil de candidato."));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM candidates WHERE user_id = ?", Long.class, user.id())).isZero();

            userService.markEmailVerified(user.id());

            JsonNode profile = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(user))).andExpect(status().isOk()));
            assertThat(profile.path("userId").asString()).isEqualTo(user.id().toString());
            assertThat(profile.path("email").asString()).isEqualTo(user.email());
            assertThat(profile.path("nationality").asString()).isEqualTo("Angolana");
            assertThat(profile.path("languages").get(0).path("language").asString()).isEqualTo("Português");
            assertThat(profile.path("languages").get(0).path("level").asString()).isEqualTo("native");
            assertThat(profile.path("sourceId").asString()).isEqualTo("portal");
            assertThat(profile.path("inTalentPool").asBoolean()).isFalse();
            assertThat(profile.path("gender").isNull()).isTrue();
            assertThat(profile.path("birthDate").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT candidate_id FROM users WHERE id = ?", UUID.class, user.id()))
                    .isEqualTo(UUID.fromString(profile.path("id").asString()));
        }

        @Test
        void aConfirmedAccountTakesOverTheCandidateOfItsEarlierApplications() throws Exception {
            TestUser user = users.create(UserRole.CANDIDATE);
            ApplicantLink applied = candidateService.findOrCreateForApplication(
                    new ApplicantData("Nome Da Candidatura", user.email().toUpperCase(), "923000111", Province.HUILA, CandidateSource.WHATSAPP));
            assertThat(applied.created()).isTrue();

            // Registered with the same email but not confirmed yet: nothing is linked
            assertThat(jdbc.queryForObject("SELECT user_id FROM candidates WHERE id = ?", UUID.class, applied.candidateId())).isNull();

            userService.markEmailVerified(user.id());

            JsonNode profile = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(user))).andExpect(status().isOk()));
            assertThat(profile.path("id").asString()).isEqualTo(applied.candidateId().toString());
            assertThat(profile.path("fullName").asString()).isEqualTo("Nome Da Candidatura");
            assertThat(profile.path("sourceId").asString()).isEqualTo("whatsapp");
        }

        @Test
        void anApplicationNeverChangesAnExistingProfile() throws Exception {
            TestUser user = candidates.verifiedCandidate();
            JsonNode before = candidates.updateProfile(user, Map.of("fullName", "Edna Mbala Kiala", "phone", "923456781"));

            ApplicantLink link = candidateService.findOrCreateForApplication(
                    new ApplicantData("Outra Pessoa Qualquer", user.email(), "934999888", Province.BENGUELA, CandidateSource.LINKEDIN));

            assertThat(link.created()).isFalse();
            assertThat(link.candidateId().toString()).isEqualTo(before.path("id").asString());
            JsonNode after = json(mvc.perform(get("/api/v1/me/candidate").with(users.bearer(user))));
            assertThat(after.path("fullName").asString()).isEqualTo("Edna Mbala Kiala");
            assertThat(after.path("phone").asString()).isEqualTo("+244923456781");
            assertThat(after.path("province").isNull()).isTrue();
            assertThat(after.path("sourceId").asString()).isEqualTo("portal");
            assertThat(after.path("updatedAt").asString()).isEqualTo(before.path("updatedAt").asString());
        }
    }

    @Nested
    class Update {

        @Test
        void partialUpdatesKeepTheRestAndTheEmailNeverChanges() throws Exception {
            TestUser user = candidates.verifiedCandidate();
            Map<String, Object> personal = new HashMap<>();
            personal.put("fullName", "Edna Mbala Kiala");
            personal.put("email", "outro@teste.ao");
            personal.put("phone", "923 456 781");
            personal.put("whatsapp", "923456781");
            personal.put("gender", "F");
            personal.put("birthDate", LocalDate.now().minusYears(30).toString());
            personal.put("province", "Luanda");
            personal.put("city", "Talatona");
            personal.put("headline", "Assistente Administrativa");
            personal.put("inTalentPool", true);
            personal.put("tagIds", List.of(UUID.randomUUID().toString()));
            JsonNode saved = candidates.updateProfile(user, personal);

            assertThat(saved.path("email").asString()).isEqualTo(user.email());
            assertThat(saved.path("phone").asString()).isEqualTo("+244923456781");
            assertThat(saved.path("whatsapp").asString()).isEqualTo("+244923456781");
            assertThat(saved.path("inTalentPool").asBoolean()).isFalse();
            assertThat(saved.path("tagIds")).isEmpty();

            Map<String, Object> skills = new HashMap<>();
            skills.put("skills", List.of("Excel", " excel ", "Primavera ERP"));
            skills.put("driverLicenses", List.of("C", "B", "B"));
            skills.put("availability", "15_days");
            skills.put("salaryExpectation", Map.of("amount", 320000, "currency", "AOA"));
            skills.put("whatsapp", "");
            JsonNode after = candidates.updateProfile(user, skills);

            assertThat(after.path("fullName").asString()).isEqualTo("Edna Mbala Kiala");
            assertThat(after.path("city").asString()).isEqualTo("Talatona");
            assertThat(after.path("whatsapp").isNull()).isTrue();
            assertThat(after.path("skills")).hasSize(2);
            assertThat(after.path("driverLicenses").get(0).asString()).isEqualTo("B");
            assertThat(after.path("driverLicenses")).hasSize(2);
            assertThat(after.path("availability").asString()).isEqualTo("15_days");
            assertThat(after.path("salaryExpectation").path("amount").decimalValue()).isEqualByComparingTo("320000");

            Map<String, Object> clear = new HashMap<>();
            Map<String, Object> noSalary = new HashMap<>();
            noSalary.put("amount", null);
            noSalary.put("currency", "AOA");
            clear.put("salaryExpectation", noSalary);
            assertThat(candidates.updateProfile(user, clear).path("salaryExpectation").isNull()).isTrue();
        }

        @Test
        void experienceAndEducationFollowTheChildCollectionRule() throws Exception {
            TestUser user = candidates.verifiedCandidate();
            TestUser other = candidates.verifiedCandidate();
            String foreign = candidates.updateProfile(other, Map.of("experience",
                    List.of(experience("exp-x", "Outra", "Motorista", "2015-01", "2016-01", false)))).path("experience").get(0).path("id").asString();

            // 5 years and 1 month ago, so the result never depends on the day of the month
            String start = YearMonth.now().minusYears(5).minusMonths(1).toString();
            JsonNode first = candidates.updateProfile(user, Map.of(
                    "experience", List.of(
                            experience("exp-1", "Seguradora Palanca", "Assistente", start, null, true),
                            experience("exp-2", "Banco BFA", "Estagiária", "2015-01", "2016-01", false)),
                    "education", List.of(education("edu-1", "licentiate", 2010, 2014))));

            // ~61 months + 12 months
            assertThat(first.path("totalExperienceYears").asInt()).isEqualTo(6);
            assertThat(first.path("experience").get(0).path("startDate").asString()).isEqualTo(start);
            assertThat(first.path("experience").get(0).path("endDate").isNull()).isTrue();
            assertThat(first.path("experience").get(1).path("endDate").asString()).isEqualTo("2016-01");
            String kept = first.path("experience").get(0).path("id").asString();
            String removed = first.path("experience").get(1).path("id").asString();
            String education = first.path("education").get(0).path("id").asString();

            JsonNode second = candidates.updateProfile(user, Map.of(
                    "experience", List.of(
                            experience(foreign, "Nova Empresa", "Motorista", "2010-01", "2012-01", false),
                            experience(kept, "Seguradora Palanca", "Assistente Sénior", start, null, true)),
                    "education", List.of(education(education, "master", 2015, 2017))));

            List<String> ids = ids(second.path("experience"));
            assertThat(ids).hasSize(2).doesNotContain(removed, foreign);
            assertThat(ids.get(1)).isEqualTo(kept);
            assertThat(second.path("experience").get(1).path("role").asString()).isEqualTo("Assistente Sénior");
            assertThat(second.path("education").get(0).path("id").asString()).isEqualTo(education);
            assertThat(second.path("education").get(0).path("level").asString()).isEqualTo("master");
            // ~61 months + 24 months
            assertThat(second.path("totalExperienceYears").asInt()).isEqualTo(7);
            // The other candidate keeps its experience
            assertThat(jdbc.queryForObject("SELECT company FROM work_experiences WHERE id = ?::uuid", String.class, foreign)).isEqualTo("Outra");
        }

        @Test
        void languagesAreReplacedWithoutCollisions() throws Exception {
            TestUser user = candidates.verifiedCandidate();
            JsonNode saved = candidates.updateProfile(user, Map.of("languages", List.of(
                    Map.of("language", "Inglês", "level", "intermediate"),
                    Map.of("language", "Português", "level", "native"))));
            assertThat(saved.path("languages")).hasSize(2);

            JsonNode changed = candidates.updateProfile(user, Map.of("languages", List.of(
                    Map.of("language", "ingles", "level", "fluent"),
                    Map.of("language", "Francês", "level", "basic"))));
            assertThat(changed.path("languages")).hasSize(2);
            assertThat(changed.path("languages").get(0).path("language").asString()).isEqualTo("ingles");
            assertThat(changed.path("languages").get(0).path("level").asString()).isEqualTo("fluent");

            candidates.patchProfile(user, Map.of("languages", List.of(
                            Map.of("language", "Inglês", "level", "basic"), Map.of("language", "INGLES", "level", "native"))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("languages"));
        }

        @Test
        void rulesOfTheProfileForms() throws Exception {
            TestUser user = candidates.verifiedCandidate();

            candidates.patchProfile(user, Map.of("birthDate", LocalDate.now().minusYears(15).toString()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                    .andExpect(jsonPath("$.errors[0].message").value("A idade tem de estar entre 16 e 80 anos."));
            candidates.patchProfile(user, Map.of("experience", List.of(experience(null, "ACME", "Técnico", "2020-05", "2019-01", false))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("experience[0].endDate"));
            candidates.patchProfile(user, Map.of("experience", List.of(experience(null, "ACME", "Técnico", YearMonth.now().plusMonths(2).toString(), null, true))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("experience[0].startDate"));
            candidates.patchProfile(user, Map.of("education", List.of(education(null, "master", 1950, null))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("education[0].startYear"));
            candidates.patchProfile(user, Map.of("whatsapp", "123"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0].field").value("whatsapp"));
            candidates.patchProfile(user, Map.of("headline", "AB", "fullName", "Edna"))
                    .andExpect(status().isBadRequest());
        }
    }
}
