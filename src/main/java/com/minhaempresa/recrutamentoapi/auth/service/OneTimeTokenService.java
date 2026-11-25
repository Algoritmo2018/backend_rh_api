package com.minhaempresa.recrutamentoapi.auth.service;

import com.minhaempresa.recrutamentoapi.auth.entity.OneTimeToken;
import com.minhaempresa.recrutamentoapi.auth.entity.OneTimeTokenType;
import com.minhaempresa.recrutamentoapi.auth.repository.OneTimeTokenRepository;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use tokens: email links (invitation, password reset, email verification) and SMS codes
 * (phone login, two-factor setup and login). Creating a token invalidates the pending ones of the
 * same type for that user. Every token expires and can be used once; codes allow a limited number of
 * attempts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private final OneTimeTokenRepository repository;
    private final TokenHasher hasher;
    private final AuthProperties properties;
    private final Clock clock;

    /** Challenge of the second login step: the token identifies it, the code goes by SMS. */
    public record TwoFactorChallenge(String challengeToken, String code, Duration validity) {

        @Override
        public String toString() {
            return "TwoFactorChallenge";
        }
    }

    // ----- Email links -----

    @Transactional
    public String createInvitationToken(UUID userId) {
        return createLink(userId, OneTimeTokenType.INVITATION, properties.invitationTtl());
    }

    @Transactional
    public String createPasswordResetToken(UUID userId) {
        return createLink(userId, OneTimeTokenType.PASSWORD_RESET, properties.passwordResetTtl());
    }

    @Transactional
    public String createEmailVerificationToken(UUID userId) {
        return createLink(userId, OneTimeTokenType.EMAIL_VERIFICATION, properties.emailVerificationTtl());
    }

    /** @return the user the token belongs to; 422 "invalid or expired link" otherwise */
    @Transactional
    public UUID consumeInvitationToken(String token) {
        return consumeLink(OneTimeTokenType.INVITATION, token);
    }

    @Transactional
    public UUID consumePasswordResetToken(String token) {
        return consumeLink(OneTimeTokenType.PASSWORD_RESET, token);
    }

    @Transactional
    public UUID consumeEmailVerificationToken(String token) {
        return consumeLink(OneTimeTokenType.EMAIL_VERIFICATION, token);
    }

    /** A confirmed email-change link: whose account and which new address (lower case). */
    public record EmailChangeTicket(UUID userId, String newEmail) {}

    /**
     * Link of a candidate's email change, bound to the user and to {@code newEmail} (lower case). Replaces
     * the pending one: only the newest request can be confirmed.
     */
    @Transactional
    public String createEmailChangeToken(UUID userId, String newEmail) {
        Instant now = clock.instant();
        repository.invalidatePending(userId, OneTimeTokenType.EMAIL_CHANGE, now);
        String token = hasher.newToken();
        repository.save(OneTimeToken.emailChange(userId, hasher.sha256(token), newEmail, now, now.plus(properties.emailChangeTtl())));
        return token;
    }

    /** @throws BusinessException 422 "invalid or expired link" */
    @Transactional
    public EmailChangeTicket consumeEmailChangeToken(String token) {
        Instant now = clock.instant();
        OneTimeToken found = repository.findByTypeAndTokenHash(OneTimeTokenType.EMAIL_CHANGE, hasher.sha256(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException("auth.token.invalid"));
        found.markUsed(now);
        return new EmailChangeTicket(found.getUserId(), found.getTargetEmail());
    }

    /**
     * Spends the pending links of the given types (e.g. a password reset cancels a pending email change; an
     * email change kills the links sent to the old address).
     */
    @Transactional
    public void invalidatePending(UUID userId, OneTimeTokenType... types) {
        Instant now = clock.instant();
        for (OneTimeTokenType type : types) {
            repository.invalidatePending(userId, type, now);
        }
    }

    /** Candidate erasure: the pending email change (with its new address) disappears. */
    @Transactional
    public void deleteEmailChangeTokens(UUID userId) {
        repository.deleteByUserAndType(userId, OneTimeTokenType.EMAIL_CHANGE);
    }

    /**
     * Daily cleanup of tokens expired for more than a day (email-change tokens hold the new address:
     * personal data is not kept longer than needed, Lei n.º 22/11).
     */
    @Scheduled(cron = "${app.auth.token-cleanup-cron:0 25 3 * * *}", zone = "${app.defaults.time-zone}")
    @Transactional
    public int deleteExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("Deleted {} expired one-time tokens", deleted);
        }
        return deleted;
    }

    /** True if the user has an unused, unexpired invitation. */
    @Transactional(readOnly = true)
    public boolean hasActiveInvitation(UUID userId) {
        return repository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(userId, OneTimeTokenType.INVITATION)
                .filter(t -> t.isUsable(clock.instant()))
                .isPresent();
    }

    // ----- SMS codes -----

    @Transactional
    public String createLoginOtp(UUID userId) {
        return createCode(userId, OneTimeTokenType.LOGIN_OTP, properties.otpTtl());
    }

    /**
     * Counts the attempt even when the code is wrong: callers that throw afterwards must not roll the
     * transaction back ({@code noRollbackFor = UnauthorizedException.class}).
     */
    @Transactional
    public boolean verifyLoginOtp(UUID userId, String code) {
        return verifyCode(userId, OneTimeTokenType.LOGIN_OTP, code, properties.otpMaxAttempts());
    }

    @Transactional
    public String createTwoFactorSetupCode(UUID userId) {
        return createCode(userId, OneTimeTokenType.TWO_FACTOR_SETUP, properties.otpTtl());
    }

    @Transactional
    public boolean verifyTwoFactorSetupCode(UUID userId, String code) {
        return verifyCode(userId, OneTimeTokenType.TWO_FACTOR_SETUP, code, properties.otpMaxAttempts());
    }

    /** Code sent to {@code newPhone}; it is only valid together with that same number. */
    @Transactional
    public String createPhoneChangeCode(UUID userId, String newPhone) {
        Instant now = clock.instant();
        repository.invalidatePending(userId, OneTimeTokenType.PHONE_CHANGE, now);
        String code = hasher.newCode();
        repository.save(new OneTimeToken(userId, OneTimeTokenType.PHONE_CHANGE, hasher.hmac(phoneBound(newPhone, code)), null, now, now.plus(properties.otpTtl())));
        return code;
    }

    /** Counts the attempt even when wrong (see {@link #verifyLoginOtp}). */
    @Transactional
    public boolean verifyPhoneChangeCode(UUID userId, String newPhone, String code) {
        Instant now = clock.instant();
        return repository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(userId, OneTimeTokenType.PHONE_CHANGE)
                .filter(t -> t.isUsable(now))
                .map(t -> checkCode(t, t.getTokenHash(), code == null ? null : phoneBound(newPhone, code), properties.otpMaxAttempts(), now))
                .orElse(false);
    }

    @Transactional
    public TwoFactorChallenge createTwoFactorChallenge(UUID userId) {
        Instant now = clock.instant();
        repository.invalidatePending(userId, OneTimeTokenType.TWO_FACTOR_LOGIN, now);
        String challenge = hasher.newToken();
        String code = hasher.newCode();
        Duration ttl = properties.twoFactorChallengeTtl();
        repository.save(new OneTimeToken(userId, OneTimeTokenType.TWO_FACTOR_LOGIN, hasher.sha256(challenge), hasher.hmac(code), now, now.plus(ttl)));
        return new TwoFactorChallenge(challenge, code, ttl);
    }

    /**
     * Checks the SMS code of a login challenge. Each wrong code counts; after the maximum the challenge
     * is spent and the user must log in again.
     *
     * @return the user id if the code is right
     */
    @Transactional
    public Optional<UUID> verifyTwoFactorChallenge(String challengeToken, String code) {
        Instant now = clock.instant();
        Optional<OneTimeToken> found = repository.findByTypeAndTokenHash(OneTimeTokenType.TWO_FACTOR_LOGIN, hasher.sha256(challengeToken))
                .filter(t -> t.isUsable(now));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OneTimeToken token = found.get();
        return checkCode(token, token.getCodeHash(), code, properties.twoFactorMaxAttempts(), now) ? Optional.of(token.getUserId()) : Optional.empty();
    }

    // ----- Internals -----

    private String createLink(UUID userId, OneTimeTokenType type, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidatePending(userId, type, now);
        String token = hasher.newToken();
        repository.save(new OneTimeToken(userId, type, hasher.sha256(token), null, now, now.plus(ttl)));
        return token;
    }

    private UUID consumeLink(OneTimeTokenType type, String token) {
        Instant now = clock.instant();
        OneTimeToken found = repository.findByTypeAndTokenHash(type, hasher.sha256(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException("auth.token.invalid"));
        found.markUsed(now);
        return found.getUserId();
    }

    private String createCode(UUID userId, OneTimeTokenType type, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidatePending(userId, type, now);
        String code = hasher.newCode();
        repository.save(new OneTimeToken(userId, type, hasher.hmac(code), null, now, now.plus(ttl)));
        return code;
    }

    private boolean verifyCode(UUID userId, OneTimeTokenType type, String code, int maxAttempts) {
        Instant now = clock.instant();
        return repository.findFirstByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .filter(t -> t.isUsable(now))
                .map(t -> checkCode(t, t.getTokenHash(), code, maxAttempts, now))
                .orElse(false);
    }

    private static String phoneBound(String phone, String code) {
        return phone + "|" + code;
    }

    private boolean checkCode(OneTimeToken token, String expectedHash, String code, int maxAttempts, Instant now) {
        int attempts = token.registerAttempt();
        boolean matches = code != null && hasher.matches(expectedHash, hasher.hmac(code));
        if (matches || attempts >= maxAttempts) {
            token.markUsed(now);
        }
        return matches;
    }
}
