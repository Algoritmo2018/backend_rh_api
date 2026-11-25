package com.minhaempresa.recrutamentoapi.messaging.controller;

import com.minhaempresa.recrutamentoapi.messaging.dto.request.MessageFilterRequest;
import com.minhaempresa.recrutamentoapi.messaging.dto.request.MessageSendRequest;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.ConversationResponse;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageResponse;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageWithRefsResponse;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Messages to candidates (page Mensagens and candidate profile). Administrators and recruiters only. */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
public class MessageController {

    private final MessageService service;

    /** Newest first, paginated ({@code page}, {@code pageSize}). */
    @GetMapping
    public PageResponse<MessageWithRefsResponse> list(@Valid MessageFilterRequest filter, @Valid PageQuery pageQuery) {
        return service.list(filter, pageQuery);
    }

    /** One entry per candidate, the most recent conversation first. */
    @GetMapping("/conversations")
    public PageResponse<ConversationResponse> conversations(@Valid PageQuery pageQuery) {
        return service.conversations(pageQuery);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@Valid @RequestBody MessageSendRequest request, AuthenticatedUser actor) {
        return service.send(request, actor);
    }
}
