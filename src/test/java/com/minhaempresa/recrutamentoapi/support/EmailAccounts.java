package com.minhaempresa.recrutamentoapi.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** SMTP accounts of the settings, created through the API (the Mailpit container or a refused port). */
public final class EmailAccounts {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    private EmailAccounts() {}

    /** Body of {@code POST /settings/email-accounts} without authentication (Mailpit accepts it). */
    public static Map<String, Object> body(String name, String host, int port, String fromAddress) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("host", host);
        body.put("port", port);
        body.put("security", "none");
        body.put("fromAddress", fromAddress);
        return body;
    }

    /** A local port with nothing listening (connections are refused at once). */
    public static int refusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    public static UUID create(MockMvc mvc, RequestPostProcessor admin, Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/v1/settings/email-accounts").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(response).path("id").asString());
    }

    /** Sets the account of one purpose ({@code null} = default server). */
    public static void route(MockMvc mvc, RequestPostProcessor admin, String purpose, UUID accountId) throws Exception {
        Map<String, Object> route = new HashMap<>();
        route.put("purpose", purpose);
        route.put("accountId", accountId == null ? null : accountId.toString());
        mvc.perform(put("/api/v1/settings/email-routes").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(List.of(route))))
                .andExpect(status().isOk());
    }
}
