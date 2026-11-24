package com.minhaempresa.recrutamentoapi.candidate.dto.request;

import com.minhaempresa.recrutamentoapi.candidate.entity.DocumentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Upload of a document ({@code POST /me/documents}, multipart; the file is the part {@code file}).
 *
 * @param reference number of the BI / NIF / INSS (required for those); spaces removed, upper case
 * @param expiresAt {@code "YYYY-MM-DD"}, not in the past
 */
public record DocumentUploadRequest(
        @NotNull(message = "{validation.required}") DocumentType type,
        String reference,
        LocalDate expiresAt) {}
