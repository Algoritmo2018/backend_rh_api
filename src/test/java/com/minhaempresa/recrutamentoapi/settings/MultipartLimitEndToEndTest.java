package com.minhaempresa.recrutamentoapi.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhaempresa.recrutamentoapi.TestcontainersConfiguration;
import com.minhaempresa.recrutamentoapi.support.TestSupportConfiguration;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The multipart limit (1 MB) is enforced by the servlet container, so it is checked against a real
 * server: a 2 MB upload gets the standard 413 error body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class, TestUsers.class})
class MultipartLimitEndToEndTest {

    @LocalServerPort
    int port;

    @Autowired
    TestUsers users;

    @Test
    void twoMegabyteUploadIs413WithTheStandardBody() {
        String token = users.accessToken(users.create(UserRole.ADMIN));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(new byte[2 * 1024 * 1024]) {
            @Override
            public String getFilename() {
                return "grande.png";
            }
        });

        Result result = RestClient.create("http://localhost:" + port).post()
                .uri("/api/v1/settings/company/logo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange((request, response) -> new Result(response.getStatusCode().value(), new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(result.status()).isEqualTo(413);
        JsonNode body = JsonMapper.builder().build().readTree(result.body());
        assertThat(body.path("error").asString()).isEqualTo("ValidationError");
        assertThat(body.path("message").asString()).isEqualTo("O ficheiro enviado é demasiado grande.");
        assertThat(body.path("path").asString()).isEqualTo("/api/v1/settings/company/logo");
    }

    private record Result(int status, String body) {}
}
