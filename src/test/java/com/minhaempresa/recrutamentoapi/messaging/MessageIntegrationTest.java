package com.minhaempresa.recrutamentoapi.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.candidate.entity.ErasureLog;
import com.minhaempresa.recrutamentoapi.candidate.service.CandidateErasureService;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.messaging.validation.TemplateVariable;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import com.minhaempresa.recrutamentoapi.support.Bell;
import com.minhaempresa.recrutamentoapi.support.CandidateApi;
import com.minhaempresa.recrutamentoapi.support.EmailAccounts;
import com.minhaempresa.recrutamentoapi.support.MailpitClient;
import com.minhaempresa.recrutamentoapi.support.MailpitClient.Email;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.RecordingSmsSender;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Messages to candidates: snapshot, channels, consent, limits, inbox, preview and erasure. */
@IntegrationTest
class MessageIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String MESSAGES = "/api/v1/messages";
    static final String TEMPLATES = "/api/v1/message-templates";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MailpitClient mailpit;

    @Autowired
    RecordingSmsSender sms;

    @Autowired
    MutableClock clock;

    @Autowired
    MessageService messageService;

    @Autowired
    CandidateErasureService erasureService;

    CandidateApi candidates;
    TestUser recruiter;
    RequestPostProcessor asRecruiter;

    @BeforeEach
    void setUp() {
        candidates = new CandidateApi(mvc, users, userService);
        recruiter = users.create(UserRole.RECRUITER);
        asRecruiter = users.bearer(recruiter);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        jdbc.update("DELETE FROM email_routes");
    }

    /** A candidate with a profile; {@code talentPool} = active talent-pool consent. */
    record Candidate(UUID id, TestUser user, String phone) {}

    Candidate candidate(String fullName, boolean talentPool) throws Exception {
        TestUser user = candidates.verifiedCandidate();
        String phone = TestUsers.uniqueLocalPhone();
        JsonNode profile = candidates.updateProfile(user, Map.of("fullName", fullName, "phone", phone));
        if (talentPool) {
            candidates.setConsent(user, "talent_pool", true);
        }
        return new Candidate(UUID.fromString(profile.path("id").asString()), user, "+244" + phone);
    }

    UUID template(String channel, String subject, String body) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Modelo " + UUID.randomUUID().toString().substring(0, 8));
        request.put("channel", channel);
        request.put("category", "general");
        request.put("subject", subject);
        request.put("body", body);
        String response = mvc.perform(post(TEMPLATES).with(asRecruiter).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(response).path("id").asString());
    }

    ResultActions send(RequestPostProcessor who, Map<String, Object> body) throws Exception {
        return mvc.perform(post(MESSAGES).with(who).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    static Map<String, Object> message(Candidate candidate, String channel, String subject, String body) {
        Map<String, Object> request = new HashMap<>();
        request.put("candidateId", candidate.id().toString());
        request.put("channel", channel);
        request.put("subject", subject);
        request.put("body", body);
        return request;
    }

    JsonNode json(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    String statusOf(String messageId) {
        return jdbc.queryForObject("SELECT status FROM messages WHERE id = ?::uuid", String.class, messageId);
    }

    // =====================================================================================

    @Test
    void anEmailFromATemplateIsFilledOnTheServerQueuedAndKeptAsASnapshot() throws Exception {
        Candidate ana = candidate("Ana Paula Mbinda", true);
        UUID templateId = template("email", "Olá {{nome}}, temos novidades", "Caro(a) {{nome_completo}},\nVeja o portal: {{link}}\n{{recrutador}}");

        Map<String, Object> request = message(ana, "email", null, null);
        request.put("templateId", templateId.toString());
        JsonNode sent = json(send(asRecruiter, request).andExpect(status().isCreated()));

        assertThat(sent.path("subject").asString()).isEqualTo("Olá Ana, temos novidades");
        String recruiterName = jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, recruiter.id());
        assertThat(sent.path("body").asString()).isEqualTo("Caro(a) Ana Paula Mbinda,\nVeja o portal: http://localhost:5173/software_rh/candidato\n" + recruiterName);
        assertThat(sent.path("status").asString()).isEqualTo("queued");
        assertThat(sent.path("direction").asString()).isEqualTo("outbound");
        assertThat(sent.path("channel").asString()).isEqualTo("email");
        assertThat(sent.path("templateId").asString()).isEqualTo(templateId.toString());
        assertThat(sent.path("templateCode").asString()).startsWith("modelo-");
        assertThat(sent.path("sentById").asString()).isEqualTo(recruiter.id().toString());

        // Delivered through the outbox with the recruitment purpose, HTML with line breaks
        Email email = mailpit.awaitEmailTo(ana.user().email());
        assertThat(email.subject()).isEqualTo("Olá Ana, temos novidades");
        assertThat(email.html()).contains("Caro(a) Ana Paula Mbinda,<br>");
        assertThat(email.text()).contains("Veja o portal:");
        String id = sent.path("id").asString();
        assertThat(jdbc.queryForObject("SELECT purpose FROM outbound_emails WHERE reference_id = ?::uuid", String.class, id)).isEqualTo("RECRUITMENT");
        await().atMost(Duration.ofSeconds(10)).until(() -> "SENT".equals(statusOf(id)));

        // Editing and deleting the template never changes what was sent
        Map<String, Object> changed = Map.of("name", "Outro nome", "channel", "email", "category", "general", "subject", "Assunto novo", "body", "Texto completamente novo.");
        mvc.perform(put(TEMPLATES + "/{id}", templateId).with(asRecruiter).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(changed)))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(TEMPLATES + "/{id}", templateId).with(asRecruiter))
                .andExpect(status().isNoContent());
        mvc.perform(get(MESSAGES).param("candidateId", ana.id().toString()).with(asRecruiter))
                .andExpect(jsonPath("$.items[0].subject").value("Olá Ana, temos novidades"))
                .andExpect(jsonPath("$.items[0].status").value("sent"))
                .andExpect(jsonPath("$.items[0].templateCode").value(sent.path("templateCode").asString()));
    }

    @Test
    void untrustedValuesNeverAddHeadersNorMarkup() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        jdbc.update("UPDATE candidates SET full_name = ? WHERE id = ?", "Ana\r\nBcc: x@evil.com <script>alert(1)</script> {{vaga}}", ana.id());

        JsonNode sent = json(send(asRecruiter, message(ana, "email", "Olá {{nome_completo}}", "Texto para {{nome_completo}} & equipa"))
                .andExpect(status().isCreated()));

        assertThat(sent.path("subject").asString()).doesNotContain("\r").doesNotContain("\n").startsWith("Olá Ana Bcc: x@evil.com");
        Email email = mailpit.awaitEmailTo(ana.user().email());
        Map<String, List<String>> headers = mailpit.headers(email.id());
        assertThat(headers).doesNotContainKey("Bcc");
        assertThat(headers.get("Subject")).singleElement().asString().doesNotContain("\n");
        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp; equipa");
        assertThat(mailpit.countTo("x@evil.com")).isZero();
    }

    @Test
    void freeTextIsPlainTextWithKnownAndAvailableVariables() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);

        send(asRecruiter, message(ana, "email", "Assunto", "<b>Olá</b> Ana"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("body"));
        send(asRecruiter, message(ana, "email", "Assunto", "Olá {{desconhecida}}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].message").value("Variável desconhecida: {{desconhecida}}."));
        send(asRecruiter, message(ana, "email", "Assunto", "Para a vaga {{vaga}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].message").value("A variável {{vaga}} ainda não pode ser usada nesta mensagem. Retire-a ou escreva o texto."));
        send(asRecruiter, message(ana, "email", null, "Sem assunto"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("subject"));
        send(asRecruiter, message(ana, "sms", null, " "))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("body"));
        send(asRecruiter, message(ana, "sms", null, "x".repeat(321)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("body"));
        Map<String, Object> noCandidate = message(ana, "email", "Assunto", "Texto");
        noCandidate.remove("candidateId");
        send(asRecruiter, noCandidate).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("candidateId"));
        send(asRecruiter, message(new Candidate(UUID.randomUUID(), null, null), "sms", null, "Olá")).andExpect(status().isNotFound());

        UUID smsTemplate = template("sms", null, "Olá {{nome}}, ligue-nos.");
        Map<String, Object> mismatch = message(ana, "email", null, null);
        mismatch.put("templateId", smsTemplate.toString());
        send(asRecruiter, mismatch).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("templateId"));
    }

    @Test
    void anEditedTemplateIsSentAsWrittenAndKeepsItsSource() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        UUID templateId = template("sms", null, "Olá {{nome}}, texto do modelo.");
        Map<String, Object> request = message(ana, "sms", null, "Olá {{nome}}, texto alterado pelo recrutador.");
        request.put("templateId", templateId.toString());

        JsonNode sent = json(send(asRecruiter, request).andExpect(status().isCreated()));

        assertThat(sent.path("body").asString()).isEqualTo("Olá Ana, texto alterado pelo recrutador.");
        assertThat(sent.path("templateId").asString()).isEqualTo(templateId.toString());
    }

    @Test
    void smsAndWhatsappAreSimulatedAndWhatsappFallsBackToSms() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);

        JsonNode viaSms = json(send(asRecruiter, message(ana, "sms", "ignorado", "Olá {{nome}}, a entrevista foi confirmada.")).andExpect(status().isCreated()));
        assertThat(viaSms.path("subject").isNull()).isTrue();
        await().atMost(Duration.ofSeconds(10)).until(() -> sms.sentTo(ana.phone()).size() == 1);
        assertThat(sms.sentTo(ana.phone())).singleElement().extracting(OutboundNotification::textBody).isEqualTo("Olá Ana, a entrevista foi confirmada.");
        await().atMost(Duration.ofSeconds(10)).until(() -> "SENT".equals(statusOf(viaSms.path("id").asString())));

        // No WhatsApp number: SMS
        JsonNode fallback = json(send(asRecruiter, message(ana, "whatsapp", null, "Mensagem por WhatsApp.")).andExpect(status().isCreated()));
        assertThat(fallback.path("channel").asString()).isEqualTo("sms");
        await().atMost(Duration.ofSeconds(10)).until(() -> sms.sentTo(ana.phone()).size() == 2);

        // With a WhatsApp number: WhatsApp (log-only sender)
        jdbc.update("UPDATE candidates SET whatsapp = ? WHERE id = ?", "+244911222333", ana.id());
        JsonNode viaWhatsapp = json(send(asRecruiter, message(ana, "whatsapp", null, "Mensagem por WhatsApp.")).andExpect(status().isCreated()));
        assertThat(viaWhatsapp.path("channel").asString()).isEqualTo("whatsapp");
        await().atMost(Duration.ofSeconds(10)).until(() -> "SENT".equals(statusOf(viaWhatsapp.path("id").asString())));
        assertThat(sms.sentTo(ana.phone())).hasSize(2);
    }

    @Test
    void withoutAnApplicationOnlyTalentPoolCandidatesAndMarketingNeedsItsConsent() throws Exception {
        Candidate outside = candidate("Bruno Neto", false);
        send(asRecruiter, message(outside, "sms", null, "Olá"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("candidateId"))
                .andExpect(jsonPath("$.message").value("Este candidato não autorizou ser contactado fora de uma candidatura (banco de talentos)."));

        Candidate pool = candidate("Carla Sousa", true);
        Map<String, Object> marketing = message(pool, "sms", null, "Feira de emprego no sábado.");
        marketing.put("marketing", true);
        send(asRecruiter, marketing).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("marketing"));
        candidates.setConsent(pool.user(), "marketing", true);
        send(asRecruiter, marketing).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE candidate_id = ? AND marketing", Integer.class, pool.id())).isEqualTo(1);

        // Revoking the talent pool stops new messages
        candidates.setConsent(pool.user(), "talent_pool", false);
        send(asRecruiter, message(pool, "sms", null, "Olá")).andExpect(status().isUnprocessableContent());

        Map<String, Object> withApplication = message(pool, "sms", null, "Olá");
        withApplication.put("applicationId", UUID.randomUUID().toString());
        send(asRecruiter, withApplication).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("applicationId"));
    }

    @Test
    void aCandidateReceivesAtMostTenMessagesPerDay() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        TestUser other = users.create(UserRole.ADMIN);
        for (int i = 0; i < 10; i++) {
            send(i % 2 == 0 ? asRecruiter : users.bearer(other), message(ana, "sms", null, "Mensagem " + i)).andExpect(status().isCreated());
        }

        String retryAfter = send(asRecruiter, message(ana, "sms", null, "Mais uma"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Este candidato já recebeu o máximo de mensagens por hoje. Tente novamente amanhã."))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(Duration.ofHours(23).toSeconds(), Duration.ofDays(1).toSeconds());

        // Automatic messages (pipeline) are not limited
        UUID templateId = template("sms", null, "Olá {{nome}}, a sua candidatura avançou.");
        MessageResponse automatic = messageService.sendFromTemplate(templateId, ana.id(), null, null, Map.of(), null, true);
        assertThat(automatic.sentById()).isNull();

        clock.advance(Duration.ofDays(1).plusSeconds(1));
        // (new token: the old one expired with the clock)
        send(users.bearer(recruiter), message(ana, "sms", null, "Novo dia")).andExpect(status().isCreated());
    }

    @Test
    void aSenderSendsAtMost100PerHour() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        Candidate filler = candidate("Outro Candidato", true);
        Instant now = clock.instant();
        for (int i = 0; i < 100; i++) {
            jdbc.update("INSERT INTO messages (id, candidate_id, channel, direction, body, status, sent_by, created_at) VALUES (?, ?, 'SMS', 'OUTBOUND', 'x', 'SENT', ?, ?)",
                    UUID.randomUUID(), filler.id(), recruiter.id(), Timestamp.from(now.minus(Duration.ofMinutes(30)).plusSeconds(i)));
        }

        String retryAfter = send(asRecruiter, message(ana, "sms", null, "Olá"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Atingiu o limite de mensagens enviadas. Tente novamente mais tarde."))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(Duration.ofMinutes(29).toSeconds(), Duration.ofMinutes(31).toSeconds());

        // Another sender is not affected; the first one again after the window
        send(users.bearer(users.create(UserRole.RECRUITER)), message(ana, "sms", null, "Olá")).andExpect(status().isCreated());
        clock.advance(Duration.ofMinutes(31));
        send(users.bearer(recruiter), message(ana, "sms", null, "Olá")).andExpect(status().isCreated());
    }

    @Test
    void sendFromTemplateUsesTheTemplateChannelAndExtraValues() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        UUID templateId = template("email", "Candidatura: {{vaga}}", "Olá {{nome}}, a vaga {{vaga}} avançou.\n{{recrutador}}");

        MessageResponse sent = messageService.sendFromTemplate(templateId, ana.id(), null, null, Map.of(TemplateVariable.JOB_TITLE, "Técnico de Frio"), null, true);

        assertThat(sent.channel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(sent.subject()).isEqualTo("Candidatura: Técnico de Frio");
        assertThat(sent.body()).isEqualTo("Olá Ana, a vaga Técnico de Frio avançou.\nEquipa de Recrutamento FEH OIRAM");
        assertThat(sent.sentById()).isNull();
        assertThat(mailpit.awaitEmailTo(ana.user().email()).subject()).isEqualTo("Candidatura: Técnico de Frio");
    }

    @Test
    void anUndeliverableEmailEndsFailedAndOnlyItsSenderIsTold() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        TestUser admin = users.create(UserRole.ADMIN);
        users.deactivateOtherAdministrators(admin);
        UUID down = EmailAccounts.create(mvc, users.bearer(admin), EmailAccounts.body(EmailAccounts.uniqueName("Em baixo"), "localhost", EmailAccounts.refusedPort(), "rh@fehoiram.test"));
        EmailAccounts.route(mvc, users.bearer(admin), "recruitment", down);
        UUID templateId = template("email", "Candidatura recebida", "Olá {{nome}}, recebemos a sua candidatura.");

        // One sent by the recruiter, one automatic (no sender)
        String manual = json(send(asRecruiter, message(ana, "email", "Assunto manual", "Texto")).andExpect(status().isCreated())).path("id").asString();
        String automatic = messageService.sendFromTemplate(templateId, ana.id(), null, null, Map.of(), null, true).id().toString();

        for (Duration delay : List.of(Duration.ZERO, Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6))) {
            clock.advance(delay);
            int expected = delay.isZero() ? 1 : attempts(manual) + 1;
            await().atMost(Duration.ofSeconds(15)).until(() -> attempts(manual) >= expected && attempts(automatic) >= expected);
            if (expected < 6) {
                assertThat(statusOf(manual)).isEqualTo("QUEUED");
                assertThat(Bell.of(jdbc, recruiter.id())).isEmpty();
            }
        }
        await().atMost(Duration.ofSeconds(5)).until(() -> "FAILED".equals(statusOf(manual)) && "FAILED".equals(statusOf(automatic)));
        assertThat(jdbc.queryForObject("SELECT failure_reason FROM messages WHERE id = ?::uuid", String.class, manual)).isNotBlank();

        // The recruiter is told about their message, pointing to the messages page
        assertThat(Bell.of(jdbc, recruiter.id())).singleElement().satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("EMAIL_FAILED");
            assertThat(bell.title()).isEqualTo("Email não entregue");
            assertThat(bell.body()).isEqualTo("Não foi possível entregar o email \"Assunto manual\" a " + ana.user().email()
                    + " após várias tentativas. Veja a mensagem em Mensagens e tente outro canal.");
            assertThat(bell.link()).isEqualTo("/app/mensagens");
        });
        // The administrators only about the automatic one, pointing to the sending accounts
        assertThat(Bell.of(jdbc, admin.id())).singleElement().satisfies(bell -> {
            assertThat(bell.type()).isEqualTo("EMAIL_FAILED");
            assertThat(bell.body()).contains("Candidatura recebida").doesNotContain("Assunto manual");
            assertThat(bell.link()).isEqualTo("/app/configuracoes");
        });
    }

    int attempts(String messageId) {
        return jdbc.queryForObject("SELECT attempts FROM outbound_emails WHERE reference_id = ?::uuid", Integer.class, messageId);
    }

    @Test
    void theInboxIsPaginatedFilteredAndGroupedByCandidate() throws Exception {
        // Ahead of every message of the other tests (some moved the clock forward), so these are the newest
        clock.advance(Duration.ofDays(3));
        asRecruiter = users.bearer(recruiter);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Candidate ana = candidate("Ana Inbox " + marker, true);
        Candidate bruno = candidate("Bruno Inbox " + marker, true);
        jdbc.update("UPDATE candidates SET whatsapp = ? WHERE id = ?", "+244911222444", bruno.id());
        send(asRecruiter, message(ana, "sms", null, "Primeira para Ana " + marker)).andExpect(status().isCreated());
        send(asRecruiter, message(bruno, "sms", null, "Para Bruno " + marker)).andExpect(status().isCreated());
        clock.advance(Duration.ofSeconds(1));
        send(asRecruiter, message(ana, "email", "Assunto " + marker, "Segunda para Ana")).andExpect(status().isCreated());

        mvc.perform(get(MESSAGES).param("candidateId", ana.id().toString()).with(asRecruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].subject").value("Assunto " + marker))
                .andExpect(jsonPath("$.items[0].candidateName").value("Ana Inbox " + marker))
                .andExpect(jsonPath("$.items[0].candidateHasWhatsapp").value(false))
                .andExpect(jsonPath("$.items[0].sentByName").value(org.hamcrest.Matchers.startsWith("Utilizador Teste")))
                .andExpect(jsonPath("$.items[0].jobTitle").doesNotExist());
        mvc.perform(get(MESSAGES).param("candidateId", ana.id().toString()).param("channel", "sms").with(asRecruiter))
                .andExpect(jsonPath("$.total").value(1));
        // Search by text or by candidate name (accents and case ignored)
        mvc.perform(get(MESSAGES).param("search", "PARA BRUNO " + marker).with(asRecruiter))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(MESSAGES).param("search", "inbóx " + marker).with(asRecruiter))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get(MESSAGES).param("search", marker).param("page", "2").param("pageSize", "2").with(asRecruiter))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get(MESSAGES + "/conversations").param("pageSize", "2").with(asRecruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].candidateId").value(ana.id().toString()))
                .andExpect(jsonPath("$.items[0].messageCount").value(2))
                .andExpect(jsonPath("$.items[0].lastMessage.subject").value("Assunto " + marker))
                .andExpect(jsonPath("$.items[1].candidateId").value(bruno.id().toString()))
                .andExpect(jsonPath("$.items[1].candidateHasWhatsapp").value(true))
                .andExpect(jsonPath("$.items[1].messageCount").value(1));
    }

    @Test
    void onlyAdministratorsAndRecruitersUseMessages() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        UUID templateId = template("sms", null, "Olá {{nome}}, ligue-nos.");
        send(users.bearer(users.create(UserRole.ADMIN)), message(ana, "sms", null, "Olá")).andExpect(status().isCreated());
        for (UserRole role : List.of(UserRole.INTERVIEWER, UserRole.CANDIDATE, UserRole.CLIENT)) {
            RequestPostProcessor who = users.bearer(users.create(role));
            mvc.perform(get(MESSAGES).with(who)).andExpect(status().isForbidden());
            mvc.perform(get(MESSAGES + "/conversations").with(who)).andExpect(status().isForbidden());
            send(who, message(ana, "sms", null, "Olá")).andExpect(status().isForbidden());
            mvc.perform(get(TEMPLATES + "/{id}/preview", templateId).param("candidateId", ana.id().toString()).with(who)).andExpect(status().isForbidden());
        }
        mvc.perform(get(MESSAGES)).andExpect(status().isUnauthorized());
    }

    @Test
    void previewFillsTheTemplateAndLeavesUnknownValuesVisible() throws Exception {
        Candidate ana = candidate("Ana Paula Mbinda", false);
        UUID templateId = template("email", "Olá {{nome}}", "{{nome_completo}}, a vaga {{vaga}} espera por si. {{recrutador}}");

        mvc.perform(get(TEMPLATES + "/{id}/preview", templateId).param("candidateId", ana.id().toString()).with(asRecruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("email"))
                .andExpect(jsonPath("$.subject").value("Olá Ana"))
                .andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.startsWith("Ana Paula Mbinda, a vaga {{vaga}} espera por si. Utilizador Teste")));
        mvc.perform(get(TEMPLATES + "/{id}/preview", templateId).param("candidateId", ana.id().toString())
                        .param("applicationId", UUID.randomUUID().toString()).with(asRecruiter))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get(TEMPLATES + "/{id}/preview", UUID.randomUUID()).param("candidateId", ana.id().toString()).with(asRecruiter))
                .andExpect(status().isNotFound());
        mvc.perform(get(TEMPLATES + "/{id}/preview", templateId).param("candidateId", UUID.randomUUID().toString()).with(asRecruiter))
                .andExpect(status().isNotFound());
    }

    @Test
    void erasingTheCandidateDeletesMessagesAndTheirEmails() throws Exception {
        Candidate ana = candidate("Ana Lopes", true);
        send(asRecruiter, message(ana, "email", "Assunto", "Texto")).andExpect(status().isCreated());
        send(asRecruiter, message(ana, "sms", null, "Olá")).andExpect(status().isCreated());
        List<UUID> ids = jdbc.queryForList("SELECT id FROM messages WHERE candidate_id = ?", UUID.class, ana.id());
        assertThat(ids).hasSize(2);

        erasureService.anonymize(ana.id(), ErasureLog.Reason.HOLDER_REQUEST, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages WHERE candidate_id = ?", Integer.class, ana.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbound_emails WHERE reference_id IN (?, ?)", Integer.class, ids.get(0), ids.get(1))).isZero();
    }
}
