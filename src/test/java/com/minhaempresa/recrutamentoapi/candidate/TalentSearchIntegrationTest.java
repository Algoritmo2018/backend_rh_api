package com.minhaempresa.recrutamentoapi.candidate;

import static com.minhaempresa.recrutamentoapi.support.CandidateApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class TalentSearchIntegrationTest {

    static final String SEARCH = "/api/v1/candidates/search";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    CandidateApi candidates;
    TestUser recruiter;
    /** Unique skill of this test (consonants only: never shortened by the stemmer). */
    String token;

    @BeforeEach
    void setUp() {
        candidates = new CandidateApi(mvc, users, userService);
        recruiter = users.create(UserRole.RECRUITER);
        String letters = "bcdfghjklmnpqrtvwxz";
        StringBuilder value = new StringBuilder("Zq");
        for (int i = 0; i < 6; i++) {
            value.append(letters.charAt(ThreadLocalRandom.current().nextInt(letters.length())));
        }
        token = value.toString();
    }

    /** A candidate in the talent pool (unless {@code consent} is false) with the given profile. */
    TestUser candidate(String name, Map<String, Object> profile, boolean consent) throws Exception {
        TestUser user = candidates.verifiedCandidate();
        Map<String, Object> body = new HashMap<>(profile);
        body.put("fullName", name);
        candidates.updateProfile(user, body);
        if (consent) {
            candidates.setConsent(user, "talent_pool", true);
        }
        return user;
    }

    JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
        return json(mvc.perform(request.with(users.bearer(recruiter))).andExpect(status().isOk()));
    }

    static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.path("items").forEach(item -> names.add(item.path("fullName").asString()));
        return names;
    }

    @Test
    void onlyConsentedCandidatesAppearAndResultsHaveNoPersonalData() throws Exception {
        candidate("Ana Consentiu Silva", Map.of("skills", List.of(token), "headline", "Operadora de empilhadeira"), true);
        candidate("Bruno Recusou Costa", Map.of("skills", List.of(token)), false);

        JsonNode page = search(get(SEARCH).param("skills", token).param("talentPoolOnly", "false"));

        assertThat(names(page)).containsExactly("Ana Consentiu Silva");
        JsonNode item = page.path("items").get(0);
        for (String hidden : List.of("email", "phone", "whatsapp", "birthDate", "gender", "nationality", "salaryExpectation", "cvDocumentId", "userId")) {
            assertThat(item.has(hidden)).as(hidden).isFalse();
        }
        assertThat(item.path("inTalentPool").asBoolean()).isTrue();
        assertThat(item.path("relevance").asInt()).isEqualTo(100);
        assertThat(page.path("pageSize").asInt()).isEqualTo(12);
    }

    @Test
    void freeTextRelevanceAccentsPrefixesAndLicenseSynonyms() throws Exception {
        candidate("Carla Logística Mendes", Map.of("headline", "Técnica de logística " + token, "skills", List.of("Gestão de armazém")), true);
        candidate("Daniel Motorista Neto", Map.of("headline", "Motorista " + token, "driverLicenses", List.of("C")), true);
        candidate("Eva Sem Carta Lopes", Map.of("headline", "Motorista " + token), true);

        // All words must reach 50%: "logistica" (no accent) and the prefix "armaz"
        assertThat(names(search(get(SEARCH).param("q", "logistica armaz " + token)))).containsExactly("Carla Logística Mendes");
        // "carta de conducao" = has a driver license; stop words ("de") do not count
        JsonNode drivers = search(get(SEARCH).param("q", "motorista com carta de conducao " + token));
        assertThat(names(drivers).getFirst()).isEqualTo("Daniel Motorista Neto");
        assertThat(drivers.path("items").get(0).path("relevance").asInt()).isEqualTo(100);
        assertThat(drivers.path("items").get(0).path("matchedTerms").toString()).contains("carta").contains("conducao");
        // 2 of 4 words → 50%, still listed
        assertThat(names(drivers)).contains("Eva Sem Carta Lopes");
        // 1 of 3 words → 33%, not listed
        assertThat(names(search(get(SEARCH).param("q", token + " cozinheiro pasteleiro")))).isEmpty();
    }

    @Test
    void filtersOfTheTalentPoolScreen() throws Exception {
        candidate("Filipe Sénior Ramos", Map.of(
                "skills", List.of("Excel Avançado", token), "province", "Benguela", "availability", "immediate",
                "experience", List.of(Map.of("company", "ACME", "role", "Contabilista", "startDate", "2010-01", "endDate", "2020-01", "current", false)),
                "education", List.of(Map.of("institution", "ISCED", "degree", "Contabilidade", "level", "master", "startYear", 2005, "endYear", 2009)),
                "languages", List.of(Map.of("language", "Inglês", "level", "fluent"))), true);
        candidate("Gina Júnior Paulo", Map.of(
                "skills", List.of("excel", token), "province", "Luanda", "availability", "30_days",
                "education", List.of(Map.of("institution", "Escola", "degree", "Ensino Médio", "level", "secondary", "startYear", 2015, "endYear", 2018)),
                "languages", List.of(Map.of("language", "Ingles", "level", "basic"))), true);

        assertThat(names(search(get(SEARCH).param("skills", token).param("skills", "EXCEL")))).hasSize(2);
        assertThat(names(search(get(SEARCH).param("skills", token).param("provinces", "Benguela")))).containsExactly("Filipe Sénior Ramos");
        assertThat(names(search(get(SEARCH).param("skills", token).param("minYears", "5")))).containsExactly("Filipe Sénior Ramos");
        assertThat(names(search(get(SEARCH).param("skills", token).param("maxYears", "1")))).containsExactly("Gina Júnior Paulo");
        assertThat(names(search(get(SEARCH).param("skills", token).param("minEducation", "licentiate")))).containsExactly("Filipe Sénior Ramos");
        assertThat(names(search(get(SEARCH).param("skills", token).param("languages", "inglês:advanced")))).containsExactly("Filipe Sénior Ramos");
        assertThat(names(search(get(SEARCH).param("skills", token).param("availability", "30_days").param("availability", "60_days"))))
                .containsExactly("Gina Júnior Paulo");
        // Most experienced first when the relevance is the same
        assertThat(names(search(get(SEARCH).param("skills", token)))).containsExactly("Filipe Sénior Ramos", "Gina Júnior Paulo");

        JsonNode page = search(get(SEARCH).param("skills", token).param("pageSize", "1").param("page", "2"));
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(names(page)).containsExactly("Gina Júnior Paulo");

        mvc.perform(get(SEARCH).param("languages", "ingles").with(users.bearer(recruiter))).andExpect(status().isBadRequest());
        mvc.perform(get(SEARCH).param("pageSize", "51").with(users.bearer(recruiter))).andExpect(status().isBadRequest());
    }

    @Test
    void skillSuggestionsAndGlobalSearch() throws Exception {
        candidate("Helena Global " + token, Map.of("skills", List.of("Soldadura " + token), "headline", "Soldadora"), false);

        JsonNode skills = json(mvc.perform(get("/api/v1/candidates/skills").with(users.bearer(recruiter))).andExpect(status().isOk()));
        assertThat(skills.toString()).contains("Soldadura " + token);

        // The global search covers every candidate (not only the talent pool), by name, headline, email or phone
        JsonNode global = json(mvc.perform(get("/api/v1/search").param("q", "helena global " + token.toLowerCase()).with(users.bearer(recruiter)))
                .andExpect(status().isOk()));
        assertThat(global.path("candidates")).hasSize(1);
        assertThat(global.path("candidates").get(0).has("email")).isFalse();
        assertThat(json(mvc.perform(get("/api/v1/search").param("q", "h").with(users.bearer(recruiter)))).path("candidates")).isEmpty();
    }
}
