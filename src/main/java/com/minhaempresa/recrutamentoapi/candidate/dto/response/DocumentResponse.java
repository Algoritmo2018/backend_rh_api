package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentStatus;
import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Frontend {@code Document}.
 *
 * @param url API path of the file ({@code /api/v1/documents/{id}/file}): fetch it with the access token
 *     (it is never a public link)
 * @param mimeType detected from the content
 * @param reference number of the BI / NIF / INSS
 */
public record DocumentResponse(
        UUID id,
        UUID candidateId,
        DocumentType type,
        String fileName,
        String mimeType,
        long sizeBytes,
        String url,
        DocumentStatus status,
        String reference,
        LocalDate expiresAt,
        UUID reviewedById,
        Instant reviewedAt,
        String rejectionNote,
        Instant uploadedAt) {}
