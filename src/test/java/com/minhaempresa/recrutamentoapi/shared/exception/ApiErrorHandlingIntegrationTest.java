package com.minhaempresa.recrutamentoapi.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class ApiErrorHandlingIntegrationTest {

    private static final String BASE = "/api/v1/test-support";
    private static final String ISO_MILLIS = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AppProperties properties;

    private static RequestPostProcessor authenticated() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"));
    }

    private static ResultActions assertProblem(ResultActions result, int status, String error, String message, String path) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.timestamp", matchesPattern(ISO_MILLIS)))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Nested
    class BusinessExceptions {

        @Test
        void notFoundIs404() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/not-found").with(authenticated())), 404, "NotFoundError", "O recurso pedido não foi encontrado.", BASE + "/not-found")
                    .andExpect(jsonPath("$.errors", empty()));
        }

        @Test
        void businessIs422WithFieldErrors() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/business").with(authenticated())), 422, "ValidationError", "Não foi possível concluir a operação.", BASE + "/business")
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].field").value("email"))
                    .andExpect(jsonPath("$.errors[0].message").value("Campo obrigatório."));
        }

        @Test
        void conflictIs409() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/conflict").with(authenticated())), 409, "ValidationError", "O pedido entra em conflito com dados já existentes.", BASE + "/conflict");
        }

        @Test
        void forbiddenIs403() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/forbidden").with(authenticated())), 403, "ForbiddenError", "Não tem permissão para realizar esta ação.", BASE + "/forbidden");
        }

        @Test
        void unauthorizedIs401() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/unauthorized").with(authenticated())), 401, "AuthError", "Inicie sessão para continuar.", BASE + "/unauthorized");
        }

        @Test
        void unexpectedIs500WithoutInternalDetails() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/boom").with(authenticated())), 500, "InternalError", "Ocorreu um erro inesperado. Tente novamente mais tarde.", BASE + "/boom")
                    .andExpect(content().string(not(containsString("internal detail"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))));
        }
    }

    @Nested
    class RequestValidation {

        @Test
        void beanValidationIs400WithFieldErrors() throws Exception {
            var request = post(BASE + "/samples").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"province\":\"Luanda\"}");
            assertProblem(mvc.perform(request), 400, "ValidationError", "Há campos inválidos. Corrija-os e tente novamente.", BASE + "/samples")
                    .andExpect(jsonPath("$.errors[0].field").value("name"))
                    .andExpect(jsonPath("$.errors[0].message").value("Campo obrigatório."));
        }

        @Test
        void unknownEnumValueInBodyIs400OnThatField() throws Exception {
            var request = post(BASE + "/samples").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\",\"province\":\"Lisboa\"}");
            assertProblem(mvc.perform(request), 400, "ValidationError", "Há campos inválidos. Corrija-os e tente novamente.", BASE + "/samples")
                    .andExpect(jsonPath("$.errors[0].field").value("province"))
                    .andExpect(jsonPath("$.errors[0].message").value("Valor inválido."));
        }

        @Test
        void malformedJsonIs400() throws Exception {
            var request = post(BASE + "/samples").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":");
            assertProblem(mvc.perform(request), 400, "ValidationError", "O pedido tem dados inválidos ou mal formatados.", BASE + "/samples");
        }

        @Test
        void validBodyUsesWireValuesAndMillisecondDates() throws Exception {
            var request = post(BASE + "/samples").with(authenticated()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\",\"province\":\"Icolo e Bengo\"}");
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.province").value("Icolo e Bengo"))
                    .andExpect(jsonPath("$.at").value("2026-11-03T09:30:00.000Z"));
        }

        @Test
        void enumQueryParameterUsesWireValue() throws Exception {
            mvc.perform(get(BASE + "/province").param("value", "Huíla").with(authenticated()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("\"Huíla\""));
        }

        @Test
        void unknownEnumQueryParameterIs400OnThatParameter() throws Exception {
            // Note: Spring's TypeConverterDelegate falls back to Enum.valueOf for query parameters, so the
            // Java constant name (LUANDA) is also accepted there. JSON bodies only accept the wire value.
            assertProblem(mvc.perform(get(BASE + "/province").param("value", "Lisboa").with(authenticated())), 400, "ValidationError",
                            "Há campos inválidos. Corrija-os e tente novamente.", BASE + "/province")
                    .andExpect(jsonPath("$.errors[0].field").value("value"));
        }
    }

    @Nested
    class Pagination {

        @Test
        void pagesStartAtOneAndMatchTheFrontendShape() throws Exception {
            mvc.perform(get(BASE + "/page").param("page", "2").param("pageSize", "10").with(authenticated()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(10)))
                    .andExpect(jsonPath("$.items[0]").value(11))
                    .andExpect(jsonPath("$.total").value(23))
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.pageSize").value(10))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }

        @Test
        void defaultsToFirstPageOfTwenty() throws Exception {
            mvc.perform(get(BASE + "/page").with(authenticated()))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.pageSize").value(20))
                    .andExpect(jsonPath("$.items", hasSize(20)));
        }

        @Test
        void invalidPageParametersAre400() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/page").param("page", "0").param("pageSize", "500").with(authenticated())), 400, "ValidationError",
                            "Há campos inválidos. Corrija-os e tente novamente.", BASE + "/page")
                    .andExpect(jsonPath("$.errors", hasSize(2)));
        }
    }

    @Nested
    class Security {

        @Test
        void missingTokenIs401FromTheFilterInTheSameFormat() throws Exception {
            assertProblem(mvc.perform(get(BASE + "/not-found")), 401, "AuthError", "Inicie sessão para continuar.", BASE + "/not-found")
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.properties").doesNotExist());
        }

        @Test
        void invalidTokenIs401() throws Exception {
            var request = get(BASE + "/admin-only").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");
            assertProblem(mvc.perform(request), 401, "AuthError", "Inicie sessão para continuar.", BASE + "/admin-only");
        }

        @Test
        void signedTokenWithRequiredRoleIsAccepted() throws Exception {
            mvc.perform(get(BASE + "/admin-only").header(HttpHeaders.AUTHORIZATION, bearer(properties.jwt().issuer(), List.of("ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
        }

        @Test
        void signedTokenWithoutRequiredRoleIs403() throws Exception {
            var request = get(BASE + "/admin-only").header(HttpHeaders.AUTHORIZATION, bearer(properties.jwt().issuer(), List.of("RECRUITER")));
            assertProblem(mvc.perform(request), 403, "ForbiddenError", "Não tem permissão para realizar esta ação.", BASE + "/admin-only");
        }

        @Test
        void tokenFromAnotherIssuerIs401() throws Exception {
            var request = get(BASE + "/admin-only").header(HttpHeaders.AUTHORIZATION, bearer("someone-else", List.of("ADMIN")));
            assertProblem(mvc.perform(request), 401, "AuthError", "Inicie sessão para continuar.", BASE + "/admin-only");
        }

        @Test
        void healthIsPublicAndOnlyHealthIsExposed() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            mvc.perform(get("/actuator/info").with(authenticated())).andExpect(status().isNotFound());
        }

        @Test
        void corsAllowsTheFrontendOrigin() throws Exception {
            mvc.perform(options(BASE + "/page")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        }

        @Test
        void corsRejectsOtherOrigins() throws Exception {
            mvc.perform(options(BASE + "/page")
                            .header(HttpHeaders.ORIGIN, "https://evil.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden());
        }

        private String bearer(String issuer, List<String> roles) {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject("test-user")
                    .issuedAt(now)
                    .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                    .claim("roles", roles)
                    .build();
            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
    }
}
