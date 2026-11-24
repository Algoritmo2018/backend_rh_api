package com.minhaempresa.recrutamentoapi.notification.entity;

import com.minhaempresa.recrutamentoapi.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** In-app notification of one user (the bell). Text stored in Portuguese when created. */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 1000, updatable = false)
    private String body;

    /** Path of the frontend, e.g. {@code /app/clientes/{id}?separador=pedidos}. */
    @Column(name = "link", length = 500, updatable = false)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(UUID userId, NotificationType type, String title, String body, String link, Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = truncate(title, 200);
        this.body = truncate(body, 1000);
        this.link = link;
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
}
