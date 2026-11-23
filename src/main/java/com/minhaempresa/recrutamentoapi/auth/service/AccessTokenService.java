package com.minhaempresa.recrutamentoapi.auth.service;

import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.security.JwtClaims;
import com.minhaempresa.recrutamentoapi.user.service.UserCredentials;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens (JWT, HS256). */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final Clock clock;

    public record IssuedAccessToken(String value, Instant expiresAt) {}

    /**
     * @param sessionId refresh-token family of this login ({@code sid} claim)
     */
    public IssuedAccessToken issue(UserCredentials user, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.ROLES, List.of(user.role().name()))
                .claim(JwtClaims.SESSION_ID, sessionId.toString());
        if (user.clientId() != null) {
            claims.claim(JwtClaims.CLIENT_ID, user.clientId().toString());
        }
        if (user.candidateId() != null) {
            claims.claim(JwtClaims.CANDIDATE_ID, user.candidateId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }
}
