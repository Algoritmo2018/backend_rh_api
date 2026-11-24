package com.minhaempresa.recrutamentoapi.notification.dto.response;

import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * Frontend {@code Notification} (plus {@code type}).
 *
 * @param link path of the frontend (relative to the app base), e.g. {@code /app/vagas/{id}}
 */
public record NotificationResponse(UUID id, UUID userId, NotificationType type, String title, String body, String link, boolean read, Instant createdAt) {}
