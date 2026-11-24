package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.dto.response.NotificationResponse;
import com.minhaempresa.recrutamentoapi.notification.dto.response.UnreadCountResponse;
import com.minhaempresa.recrutamentoapi.notification.entity.Notification;
import com.minhaempresa.recrutamentoapi.notification.entity.NotificationType;
import com.minhaempresa.recrutamentoapi.notification.mapper.NotificationMapper;
import com.minhaempresa.recrutamentoapi.notification.repository.NotificationRepository;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationProperties;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications (the bell). Created by the modules in the transaction of their event; each user
 * only ever sees and changes their own (another user's id → 404). The frontend polls every minute
 * ({@link #unreadCount} is the light call).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** What the bell shows without paging (frontend: the 30 newest). */
    public static final int DEFAULT_LIST_SIZE = 30;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final NotificationProperties properties;
    private final Clock clock;

    /** One notification per recipient (repeated ids once, {@code null} ids ignored). */
    @Transactional
    public void notify(Collection<UUID> userIds, NotificationType type, String title, String body, String link) {
        Instant now = clock.instant();
        new LinkedHashSet<>(userIds).stream()
                .filter(Objects::nonNull)
                .forEach(userId -> repository.save(new Notification(userId, type, title, body, link, now)));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> latest(AuthenticatedUser actor) {
        return repository.findByUserId(actor.id(), PageRequest.of(0, DEFAULT_LIST_SIZE, NEWEST_FIRST)).map(mapper::toResponse).getContent();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> page(AuthenticatedUser actor, PageQuery pageQuery) {
        return PageResponse.from(repository.findByUserId(actor.id(), pageQuery.toPageable(NEWEST_FIRST)), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(AuthenticatedUser actor) {
        return new UnreadCountResponse(repository.countByUserIdAndReadAtIsNull(actor.id()));
    }

    @Transactional
    public void markRead(UUID id, AuthenticatedUser actor) {
        repository.findByIdAndUserId(id, actor.id())
                .orElseThrow(() -> new NotFoundException("notification.not-found"))
                .markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(AuthenticatedUser actor) {
        repository.markAllRead(actor.id(), clock.instant());
    }

    /** Daily: read notifications older than 90 days and every one older than 180 days are deleted. */
    @Scheduled(cron = "${app.notifications.cleanup-cron:0 15 3 * * *}", zone = "${app.defaults.time-zone}")
    @Transactional
    public int deleteExpired() {
        Instant now = clock.instant();
        int deleted = repository.deleteExpired(now.minus(properties.readRetention()), now.minus(properties.retention()));
        if (deleted > 0) {
            log.info("Deleted {} old notifications", deleted);
        }
        return deleted;
    }
}
