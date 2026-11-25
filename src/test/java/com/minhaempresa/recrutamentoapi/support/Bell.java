package com.minhaempresa.recrutamentoapi.support;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads a user's in-app notifications (the bell) straight from the database. */
public final class Bell {

    private Bell() {}

    /** One notification as stored ({@code type} is the Java constant, e.g. {@code JOB_ASSIGNED}). */
    public record Entry(String type, String title, String body, String link) {}

    /** The user's notifications, newest first. */
    public static List<Entry> of(JdbcTemplate jdbc, UUID userId) {
        return jdbc.query("SELECT type, title, body, link FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id",
                (rs, i) -> new Entry(rs.getString("type"), rs.getString("title"), rs.getString("body"), rs.getString("link")), userId);
    }
}
