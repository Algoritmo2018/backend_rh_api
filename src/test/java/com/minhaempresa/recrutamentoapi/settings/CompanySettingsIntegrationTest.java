package com.minhaempresa.recrutamentoapi.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.shared.storage.StorageProperties;
import com.minhaempresa.recrutamentoapi.support.AuthApi;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CompanySettingsIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StorageProperties storageProperties;

    AuthApi api;
    TestUser admin;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc);
        admin = users.create(UserRole.ADMIN);
    }

    @AfterEach
    void removeLogo() throws Exception {
        mvc.perform(delete("/api/v1/settings/company/logo").with(users.bearer(admin)));
    }

    private static byte[] png(int size) {
        byte[] content = Arrays.copyOf(PNG_SIGNATURE, size);
        Arrays.fill(content, PNG_SIGNATURE.length, size, (byte) 7);
        return content;
    }

    private ResultActions upload(String name, String contentType, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/settings/company/logo").file(new MockMultipartFile("file", name, contentType, content)).with(users.bearer(admin)));
    }

    private JsonNode company() throws Exception {
        return JSON.readTree(mvc.perform(get("/api/v1/settings/company").with(users.bearer(admin))).andReturn().getResponse().getContentAsString());
    }

    @Test
    void thereIsAlwaysExactlyOneRow() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_settings", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO company_settings SELECT 2, name, legal_name, nif, email, phone, address, website, NULL, NULL, NULL, NULL, "
                        + "data_retention_months, consent_text_version, consent_job_text, consent_talent_pool_text, created_at, updated_at, NULL, NULL FROM company_settings"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void partialUpdateChangesOnlyTheSentFields() throws Exception {
        JsonNode before = company();

        api.sendJson("PATCH", "/api/v1/settings/company", users.accessToken(admin), Map.of("phone", "923 111 222", "address", "Avenida 4 de Fevereiro, Luanda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+244923111222"))
                .andExpect(jsonPath("$.address").value("Avenida 4 de Fevereiro, Luanda"))
                .andExpect(jsonPath("$.name").value(before.path("name").asString()))
                .andExpect(jsonPath("$.consentTextVersion").value(before.path("consentTextVersion").asString()));

        assertThat(jdbc.queryForObject("SELECT updated_by FROM company_settings", UUID.class)).isEqualTo(admin.id());
        api.sendJson("PATCH", "/api/v1/settings/company", users.accessToken(admin), Map.of("phone", before.path("phone").asString(), "address", before.path("address").asString()));
    }

    @Test
    void invalidValuesAre400() throws Exception {
        api.sendJson("PATCH", "/api/v1/settings/company", users.accessToken(admin), Map.of("dataRetentionMonths", 5, "nif", "12AB", "website", "fehoiram"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void changingAConsentTextCreatesANewVersionAndKeepsTheExactTexts() throws Exception {
        JsonNode before = company();
        String newText = "Autorizo o tratamento dos meus dados para esta vaga, nos termos da Lei n.º 22/11 (versão de teste " + UUID.randomUUID() + ").";

        JsonNode after = JSON.readTree(api.sendJson("PATCH", "/api/v1/settings/company", users.accessToken(admin), Map.of("consentJobText", newText))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String version = after.path("consentTextVersion").asString();
        assertThat(version).isNotEqualTo(before.path("consentTextVersion").asString()).matches("\\d{4}\\.\\d+");
        var saved = jdbc.queryForMap("SELECT job_text, talent_pool_text, created_by FROM consent_text_versions WHERE version = ?", version);
        assertThat(saved.get("job_text")).isEqualTo(newText);
        assertThat(saved.get("talent_pool_text")).isEqualTo(before.path("consentTalentPoolText").asString());
        assertThat(saved.get("created_by")).isEqualTo(admin.id());

        // Same text again: no new version
        api.sendJson("PATCH", "/api/v1/settings/company", users.accessToken(admin), Map.of("consentJobText", newText))
                .andExpect(jsonPath("$.consentTextVersion").value(version));
        // The public endpoint shows the new text at once (cache evicted)
        mvc.perform(get("/api/v1/public/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentJobText").value(newText))
                .andExpect(jsonPath("$.nif").doesNotExist());
    }

    @Test
    void logoIsVersionedAndCachedForAYear() throws Exception {
        upload("logo.png", "image/png", png(2_000)).andExpect(status().isOk());
        String logoUrl = company().path("logoUrl").asString();
        assertThat(logoUrl).matches("/api/v1/public/settings/logo\\?v=[0-9a-f]{12}");
        String version = logoUrl.substring(logoUrl.indexOf("v=") + 2);

        mvc.perform(get(logoUrl))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + version + "\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("max-age=31536000"),
                        org.hamcrest.Matchers.containsString("public"),
                        org.hamcrest.Matchers.containsString("immutable"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/v1/public/settings/logo"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
        mvc.perform(get("/api/v1/public/settings/logo").header(HttpHeaders.IF_NONE_MATCH, "\"" + version + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void storageKeyIsGeneratedAndTheOldFileIsRemoved() throws Exception {
        upload("../../evil name.png", "image/png", png(1_000)).andExpect(status().isOk());
        String firstKey = jdbc.queryForObject("SELECT logo_key FROM company_settings", String.class);
        assertThat(firstKey).matches("company-logo/[0-9a-f-]{36}\\.png");
        assertThat(jdbc.queryForObject("SELECT logo_original_name FROM company_settings", String.class)).isEqualTo("../../evil name.png");
        Path root = Path.of(storageProperties.local().root());
        assertThat(Files.exists(root.resolve(firstKey))).isTrue();

        upload("novo.png", "image/png", png(1_500)).andExpect(status().isOk());

        assertThat(Files.exists(root.resolve(firstKey))).as("previous file deleted after commit").isFalse();
    }

    @Test
    void onlyPngJpegAndWebpUpTo300KbAreAccepted() throws Exception {
        upload("big.png", "image/png", png(300 * 1024 + 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("A imagem tem de ter no máximo 300 KB."));
        upload("logo.png", "image/png", "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Use uma imagem PNG, JPG ou WebP."));
        upload("logo.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableContent());
    }

    /** The 1 MB request limit is enforced by Tomcat (not by MockMvc): check the effective configuration. */
    @Test
    void multipartLimitsAreOneMegabyte(@Autowired org.springframework.boot.servlet.autoconfigure.MultipartProperties multipart) {
        assertThat(multipart.getMaxFileSize().toMegabytes()).isEqualTo(1);
        assertThat(multipart.getMaxRequestSize().toMegabytes()).isEqualTo(1);
    }

    @Test
    void withoutLogoThePublicEndpointIs404() throws Exception {
        mvc.perform(delete("/api/v1/settings/company/logo").with(users.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());

        mvc.perform(get("/api/v1/public/settings/logo")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NotFoundError"));
    }
}
