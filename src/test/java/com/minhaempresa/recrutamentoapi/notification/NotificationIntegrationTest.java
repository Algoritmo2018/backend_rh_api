package com.minhaempresa.recrutamentoapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minhaempresa.recrutamentoapi.IntegrationTest;
import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import com.minhaempresa.recrutamentoapi.notification.service.NotificationService;
import com.minhaempresa.recrutamentoapi.support.MutableClock;
import com.minhaempresa.recrutamentoapi.support.TestUsers;
import com.minhaempresa.recrutamentoapi.support.TestUsers.TestUser;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The bell: each user sees and changes only their own notifications. */
@IntegrationTest
class NotificationIntegrationTest {

    static final String BELL = "/api/v1/notifications";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationService notifications;

    @Autowired
    MutableClock clock;

    TestUser ana;
    TestUser bruno;
    RequestPostProcessor asAna;

    @BeforeEach
    void setUp() {
        ana = users.create(UserRole.RECRUITER);
        bruno = users.create(UserRole.CLIENT);
        asAna = users.bearer(ana);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    void notify(TestUser user, String title) {
        notifications.notify(List.of(user.id()), NotificationType.JOB_ASSIGNED, title, "Corpo " + title, "/app/vagas/" + UUID.randomUUID());
        // Distinct creation times, so the order is deterministic
        clock.advance(Duration.ofMillis(5));
    }

    UUID idOf(TestUser user, String title) {
        return jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ? AND title = ?", UUID.class, user.id(), title);
    }

    @Test
    void theListHasOnlyOwnNotificationsNewestFirst() throws Exception {
        notify(ana, "Primeira");
        notify(bruno, "Do Bruno");
        notify(ana, "Segunda");

        mvc.perform(get(BELL).with(asAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Segunda"))
                .andExpect(jsonPath("$[0].body").value("Corpo Segunda"))
                .andExpect(jsonPath("$[0].type").value("job_assigned"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].userId").value(ana.id().toString()))
                .andExpect(jsonPath("$[0].link").exists())
                .andExpect(jsonPath("$[1].title").value("Primeira"));

        mvc.perform(get(BELL).param("page", "2").param("pageSize", "1").with(asAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Primeira"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void withoutPagingTheBellShowsThe30Newest() throws Exception {
        for (int i = 1; i <= 32; i++) {
            notify(ana, "N" + i);
        }

        mvc.perform(get(BELL).with(asAna))
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].title").value("N32"))
                .andExpect(jsonPath("$[29].title").value("N3"));
        mvc.perform(get(BELL + "/unread-count").with(asAna)).andExpect(jsonPath("$.count").value(32));
    }

    @Test
    void markingAsReadUpdatesTheCounter() throws Exception {
        notify(ana, "Um");
        notify(ana, "Dois");
        notify(ana, "Tres");
        mvc.perform(get(BELL + "/unread-count").with(asAna)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));

        UUID first = idOf(ana, "Um");
        mvc.perform(post(BELL + "/{id}/read", first).with(asAna)).andExpect(status().isNoContent());
        // Idempotent
        mvc.perform(post(BELL + "/{id}/read", first).with(asAna)).andExpect(status().isNoContent());
        mvc.perform(get(BELL + "/unread-count").with(asAna)).andExpect(jsonPath("$.count").value(2));

        mvc.perform(post(BELL + "/read-all").with(asAna)).andExpect(status().isNoContent());
        mvc.perform(get(BELL + "/unread-count").with(asAna)).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get(BELL).with(asAna)).andExpect(jsonPath("$[*].read").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void anotherUsersNotificationIsNotFoundAndNeverChanged() throws Exception {
        notify(bruno, "Privada");
        UUID theirs = idOf(bruno, "Privada");

        mvc.perform(post(BELL + "/{id}/read", theirs).with(asAna))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notificação não encontrada."));
        mvc.perform(post(BELL + "/{id}/read", UUID.randomUUID()).with(asAna)).andExpect(status().isNotFound());
        mvc.perform(post(BELL + "/read-all").with(asAna)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, theirs)).isNull();
        mvc.perform(get(BELL + "/unread-count").with(users.bearer(bruno))).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void everyRoleHasABellButOnlyWhenLoggedIn() throws Exception {
        mvc.perform(get(BELL)).andExpect(status().isUnauthorized());
        mvc.perform(get(BELL + "/unread-count")).andExpect(status().isUnauthorized());
        for (UserRole role : Arrays.asList(UserRole.ADMIN, UserRole.INTERVIEWER, UserRole.CANDIDATE, UserRole.CLIENT)) {
            mvc.perform(get(BELL + "/unread-count").with(users.bearer(users.create(role)))).andExpect(status().isOk());
        }
    }

    @Test
    void oldNotificationsAreDeletedDaily() {
        Instant now = clock.instant();
        UUID readOld = insert(ana, now.minus(Duration.ofDays(91)), true);
        UUID readRecent = insert(ana, now.minus(Duration.ofDays(89)), true);
        UUID unreadOld = insert(ana, now.minus(Duration.ofDays(179)), false);
        UUID unreadVeryOld = insert(ana, now.minus(Duration.ofDays(181)), false);

        notifications.deleteExpired();

        assertThat(exists(readOld)).isFalse();
        assertThat(exists(unreadVeryOld)).isFalse();
        assertThat(exists(readRecent)).isTrue();
        assertThat(exists(unreadOld)).isTrue();
    }

    @Test
    void notificationsDisappearWithTheirUser() {
        notify(bruno, "Apagar");
        jdbc.update("DELETE FROM users WHERE id = ?", bruno.id());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Integer.class, bruno.id())).isZero();
    }

    UUID insert(TestUser user, Instant createdAt, boolean read) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO notifications (id, user_id, type, title, body, created_at, read_at) VALUES (?, ?, 'JOB_ASSIGNED', 'T', 'B', ?, ?)",
                id, user.id(), Timestamp.from(createdAt), read ? Timestamp.from(createdAt) : null);
        return id;
    }

    boolean exists(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE id = ?", Integer.class, id) > 0;
    }
}
