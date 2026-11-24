package com.minhaempresa.recrutamentoapi.candidate.dto.response;

import com.minhaempresa.recrutamentoapi.candidate.entity.ConsentType;
import java.time.Instant;
import java.util.UUID;

/** Frontend {@code Consent}: which purpose, which text version, when granted and when revoked. */
public record ConsentResponse(
        UUID id,
        UUID candidateId,
        ConsentType type,
        UUID jobId,
        boolean granted,
        String textVersion,
        Instant grantedAt,
        Instant revokedAt) {}
