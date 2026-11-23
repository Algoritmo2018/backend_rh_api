package com.minhaempresa.recrutamentoapi.messaging.dto.request;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.entity.MessageTemplateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or edit a message template (frontend {@code TemplateFormDialog}). Plain text only; SMS
 * length, email subject and variables are checked by the service.
 */
public record MessageTemplateRequest(
        @NotBlank(message = "{validation.required}") @Size(min = 3, max = 80, message = "{template.name.size}") String name,
        @NotNull(message = "{validation.required}") MessageChannel channel,
        @NotNull(message = "{validation.required}") MessageTemplateCategory category,
        @Size(max = 150, message = "{template.subject.size}") String subject,
        @NotBlank(message = "{validation.required}") @Size(min = 10, max = 4000, message = "{template.body.size}") String body) {}
