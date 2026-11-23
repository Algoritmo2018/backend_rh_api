package com.minhaempresa.recrutamentoapi.auth.service;

import com.minhaempresa.recrutamentoapi.auth.entity.RefreshToken;
import com.minhaempresa.recrutamentoapi.auth.repository.RefreshTokenRepository;
import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import com.minhaempresa.recrutamentoapi.shared.exception.UnauthorizedException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens with rotation and reuse detection.
 *
 * <ul>
 *   <li>Every login starts a family; every refresh replaces the token by a new one of the same family.
 *   <li>A family lives at most {@code app.jwt.refresh-family-max-age} (then a new login is needed).
 *   <li>Presenting an already rotated token within {@code refresh-reuse-grace-period} (two tabs
 *       refreshing at the same time) is rejected without side effects; after that period it is
 *       treated as theft and the whole family is revoked.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final TokenHasher hasher;
    private final AppProperties properties;
    private final Clock clock;

    /** A refresh token to send to the client (only here is the raw value available). */
    public record IssuedRefreshToken(String value, UUID familyId, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedRefreshToken(familyId=" + familyId + ")";
        }
    }

    public record Rotation(UUID userId, IssuedRefreshToken next) {}

    /** Starts a new login session (family). */
    @Transactional
    public IssuedRefreshToken issueForNewSession(UUID userId, String userAgent, String ipAddress) {
        Instant now = clock.instant();
        return save(userId, UUID.randomUUID(), now, now, userAgent, ipAddress).issued();
    }

    /**
     * Exchanges a refresh token for a new one of the same family.
     *
     * @throws UnauthorizedException if the token is unknown, expired, revoked or reused
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken, String userAgent, String ipAddress) {
        Instant now = clock.instant();
        RefreshToken current = find(rawToken).orElseThrow(RefreshTokenService::sessionExpired);

        if (current.isRevoked()) {
            boolean withinGrace = current.wasRotated() && now.isBefore(current.getRotatedAt().plus(properties.jwt().refreshReuseGracePeriod()));
            if (!withinGrace && current.wasRotated()) {
                log.warn("Reuse of a rotated refresh token for user {}: revoking session {}", current.getUserId(), current.getFamilyId());
                repository.revokeFamily(current.getFamilyId(), now);
            }
            throw sessionExpired();
        }
        if (!current.getExpiresAt().isAfter(now) || familyExpired(current, now)) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw sessionExpired();
        }

        Saved next = save(current.getUserId(), current.getFamilyId(), current.getFamilyCreatedAt(), now, userAgent, ipAddress);
        current.rotate(next.entity().getId(), now);
        return new Rotation(current.getUserId(), next.issued());
    }

    /** Logout: revokes the family of the token, if it exists. */
    @Transactional
    public void revokeSessionOf(String rawToken) {
        find(rawToken).ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Ends every session of a user (deactivation, role change, password reset). */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        repository.revokeAllOfUser(userId, clock.instant());
    }

    /** Ends every session except the current one (password change). */
    @Transactional
    public void revokeOtherSessions(UUID userId, UUID currentSessionId) {
        if (currentSessionId == null) {
            revokeAllSessions(userId);
        } else {
            repository.revokeAllOfUserExcept(userId, currentSessionId, clock.instant());
        }
    }

    private Optional<RefreshToken> find(String rawToken) {
        return rawToken == null || rawToken.isBlank() ? Optional.empty() : repository.findByTokenHash(hasher.sha256(rawToken));
    }

    private boolean familyExpired(RefreshToken token, Instant now) {
        return !token.getFamilyCreatedAt().plus(properties.jwt().refreshFamilyMaxAge()).isAfter(now);
    }

    private Saved save(UUID userId, UUID familyId, Instant familyCreatedAt, Instant now, String userAgent, String ipAddress) {
        Instant familyEnd = familyCreatedAt.plus(properties.jwt().refreshFamilyMaxAge());
        Instant expiresAt = min(now.plus(properties.jwt().refreshTokenTtl()), familyEnd);
        String value = hasher.newToken();
        RefreshToken entity = repository.save(new RefreshToken(userId, familyId, familyCreatedAt, hasher.sha256(value), now, expiresAt, truncate(userAgent, 255), truncate(ipAddress, 45)));
        return new Saved(entity, new IssuedRefreshToken(value, familyId, expiresAt));
    }

    private record Saved(RefreshToken entity, IssuedRefreshToken issued) {}

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static UnauthorizedException sessionExpired() {
        return new UnauthorizedException("auth.session.expired");
    }
}
