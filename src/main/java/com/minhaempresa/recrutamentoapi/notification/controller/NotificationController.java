package com.minhaempresa.recrutamentoapi.notification.controller;

import com.minhaempresa.recrutamentoapi.notification.dto.response.UnreadCountResponse;
import com.minhaempresa.recrutamentoapi.notification.service.NotificationService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The bell of every authenticated user: only their own notifications. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    /** The 30 newest ({@code Notification[]}), or a {@code PageResponse} with {@code page}/{@code pageSize}. */
    @GetMapping
    public Object list(@Valid PageQuery pageQuery, AuthenticatedUser actor) {
        if (pageQuery.page() == null && pageQuery.pageSize() == null) {
            return service.latest(actor);
        }
        return service.page(actor, pageQuery);
    }

    /** Light call for the polling of the bell (every minute). */
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(AuthenticatedUser actor) {
        return service.unreadCount(actor);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, AuthenticatedUser actor) {
        service.markRead(id, actor);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(AuthenticatedUser actor) {
        service.markAllRead(actor);
    }
}
