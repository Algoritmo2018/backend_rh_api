package com.minhaempresa.recrutamentoapi.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the emails captured by the Mailpit container through its REST API. */
public class MailpitClient {

    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public MailpitClient(String baseUrl) {
        this.http = RestClient.create(baseUrl);
    }

    /** An email as stored by Mailpit. */
    public record Email(String id, String subject, String text, String html) {

        /** The {@code token} query parameter of the first link in the email. */
        public String token() {
            Matcher matcher = TOKEN.matcher(text);
            if (!matcher.find()) {
                throw new AssertionError("No token link in email: " + text);
            }
            return matcher.group(1);
        }
    }

    /** Summaries of the emails sent to an address, newest first. */
    public List<String> idsTo(String address) {
        String body = http.get().uri("/api/v1/search?query={q}", "to:\"" + address + "\"").retrieve().body(String.class);
        List<String> ids = new ArrayList<>();
        for (JsonNode message : json.readTree(body).path("messages")) {
            ids.add(message.path("ID").asString());
        }
        return ids;
    }

    public int countTo(String address) {
        return idsTo(address).size();
    }

    /** Waits until at least {@code count} emails reached the address and returns the newest one. */
    public Email awaitEmailTo(String address, int count) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> countTo(address) >= count);
        return read(idsTo(address).getFirst());
    }

    public Email awaitEmailTo(String address) {
        return awaitEmailTo(address, 1);
    }

    private Email read(String id) {
        JsonNode message = json.readTree(http.get().uri("/api/v1/message/{id}", id).retrieve().body(String.class));
        return new Email(id, message.path("Subject").asString(), message.path("Text").asString(), message.path("HTML").asString());
    }
}
