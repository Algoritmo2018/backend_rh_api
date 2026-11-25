package com.minhaempresa.recrutamentoapi.messaging.dto.request;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Frontend {@code SendMessageInput} plus {@code marketing}. Plain text only (HTML tags are refused).
 *
 * <p>With {@code templateId} and a blank {@code body}, the server fills the template (subject included).
 * With a {@code body}, it is sent as written (the template is only recorded as its source), so the
 * recruiter may edit a template before sending. Variables ({@code {{nome}}}…) are filled in both cases.
 *
 * @param applicationId must be {@code null} until applications exist (stage 8)
 * @param marketing a marketing communication: the candidate must have accepted marketing
 */
public record MessageSendRequest(
        @NotNull(message = "{validation.required}") UUID candidateId,
        UUID applicationId,
        @NotNull(message = "{validation.required}") MessageChannel channel,
        @Size(max = 150, message = "{template.subject.size}") String subject,
        @Size(max = 4000, message = "{message.body.size}") String body,
        UUID templateId,
        Boolean marketing) {}
