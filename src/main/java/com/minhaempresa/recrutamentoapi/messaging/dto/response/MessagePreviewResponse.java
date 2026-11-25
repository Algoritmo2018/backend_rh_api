package com.minhaempresa.recrutamentoapi.messaging.dto.response;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;

/**
 * A template filled for one candidate (frontend {@code preview}). Plain text; variables without a value stay
 * visible ({@code {{vaga}}}).
 *
 * @param subject {@code null} for SMS and WhatsApp
 */
public record MessagePreviewResponse(MessageChannel channel, String subject, String body) {}
