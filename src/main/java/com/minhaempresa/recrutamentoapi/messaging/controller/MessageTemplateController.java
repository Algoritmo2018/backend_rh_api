package com.minhaempresa.recrutamentoapi.messaging.controller;

import com.minhaempresa.recrutamentoapi.messaging.dto.request.MessageTemplateRequest;
import com.minhaempresa.recrutamentoapi.messaging.dto.response.MessageTemplateResponse;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Message templates: read by every internal role (candidate profile), edited by admin and recruiter. */
@RestController
@RequestMapping("/api/v1/message-templates")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public List<MessageTemplateResponse> list(@RequestParam(required = false) MessageChannel channel) {
        return service.listActive(channel);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public MessageTemplateResponse create(@Valid @RequestBody MessageTemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public MessageTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody MessageTemplateRequest request) {
        return service.update(id, request);
    }

    /** Deactivates the template (sent messages keep their reference). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public void delete(@PathVariable UUID id) {
        service.deactivate(id);
    }
}
