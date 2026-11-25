package com.minhaempresa.recrutamentoapi.auth.service;

import com.minhaempresa.recrutamentoapi.auth.dto.request.AcceptInvitationRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.ConfirmEmailChangeRequest;
import com.minhaempresa.recrutamentoapi.auth.entity.OneTimeTokenType;
import com.minhaempresa.recrutamentoapi.auth.service.OneTimeTokenService.EmailChangeTicket;
import com.minhaempresa.recrutamentoapi.user.service.UserService.AppliedEmailChange;
import com.minhaempresa.recrutamentoapi.auth.dto.request.ForgotPasswordRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.LoginRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.OtpRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.OtpVerifyRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.RegisterRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.ResetPasswordRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.TwoFactorLoginRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.request.VerifyEmailRequest;
import com.minhaempresa.recrutamentoapi.auth.dto.response.CodeSentResponse;
import com.minhaempresa.recrutamentoapi.auth.dto.response.LoginResponse;
import com.minhaempresa.recrutamentoapi.auth.dto.response.SessionResponse;
import com.minhaempresa.recrutamentoapi.auth.event.EmailVerificationRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.event.PasswordResetRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.event.SmsCodeRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.service.AccessTokenService.IssuedAccessToken;
import com.minhaempresa.recrutamentoapi.auth.service.RefreshTokenService.IssuedRefreshToken;
import com.minhaempresa.recrutamentoapi.auth.service.RefreshTokenService.Rotation;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationChannel;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.exception.UnauthorizedException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import com.minhaempresa.recrutamentoapi.user.event.SecurityAlertEvent;
import com.minhaempresa.recrutamentoapi.user.service.UserCredentials;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (password, two-step, SMS code), registration, sessions and password/email/invitation flows.
 *
 * <p>Never reveals whether an account exists: wrong email, wrong password, locked or inactive account
 * all answer the same 401, and "forgot password"/"send SMS code" always answer the same way.
 *
 * <p>Methods that count failed attempts and then throw use {@code noRollbackFor =
 * UnauthorizedException.class}, so the counters are saved.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final UserService users;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final OneTimeTokenService oneTimeTokens;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final NotificationDispatcher notifications;
    private final AuthProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Hash compared when the account does not exist, so the response takes as long as a real check. */
    private volatile String dummyPasswordHash;

    /** Where the request comes from (stored with the refresh token, used for rate limits). */
    public record ClientInfo(String ipAddress, String userAgent) {}

    /** A new session: the body for the client and the refresh token for the cookie. */
    public record AuthResult(SessionResponse session, IssuedRefreshToken refreshToken) {}

    /** {@code refreshToken} is null when a second step (two-factor) is required. */
    public record LoginResult(LoginResponse response, IssuedRefreshToken refreshToken) {}

    // ----- Password login -----

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResult login(LoginRequest request, ClientInfo client) {
        rateLimiter.check("login", client.ipAddress(), properties.rateLimits().login());
        Optional<UserCredentials> found = users.findCredentialsByEmail(request.email());
        Instant now = clock.instant();
        if (found.isEmpty() || !found.get().active() || !found.get().hasPassword() || found.get().isLocked(now)) {
            passwordEncoder.matches(request.password(), dummyPasswordHash());
            throw invalidLogin();
        }
        UserCredentials user = found.get();
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            users.registerFailedLogin(user.id());
            throw invalidLogin();
        }
        if (user.twoFactorEnabled()) {
            var challenge = oneTimeTokens.createTwoFactorChallenge(user.id());
            events.publishEvent(new SmsCodeRequestedEvent(user.phone(), SmsCodeRequestedEvent.Purpose.TWO_FACTOR, challenge.code(), challenge.validity()));
            return new LoginResult(LoginResponse.challenge(challenge.challengeToken(), challenge.validity().toSeconds()), null);
        }
        AuthResult result = startSession(user, client);
        return new LoginResult(LoginResponse.session(result.session()), result.refreshToken());
    }

    /** Second step: the challenge from {@link #login} plus the SMS code (5 attempts, 5 minutes). */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult loginWithTwoFactor(TwoFactorLoginRequest request, ClientInfo client) {
        rateLimiter.check("otp-verify", client.ipAddress(), properties.rateLimits().otpVerify());
        UUID userId = oneTimeTokens.verifyTwoFactorChallenge(request.challengeToken(), request.code()).orElseThrow(AuthService::invalidCode);
        UserCredentials user = users.getCredentials(userId);
        if (!user.active()) {
            throw invalidCode();
        }
        return startSession(user, client);
    }

    // ----- Phone + SMS code login -----

    /** Always the same answer, whether the phone has an eligible account or not. */
    @Transactional
    public CodeSentResponse requestOtp(OtpRequest request, ClientInfo client) {
        requireOtpLoginEnabled();
        rateLimiter.check("otp-request", client.ipAddress(), properties.rateLimits().otpRequest());
        String phone = PhoneNumbers.normalizeOrThrow(request.phone());
        rateLimiter.check("otp-phone-cooldown", phone, 1, properties.otpResendCooldown());
        rateLimiter.check("otp-phone-hourly", phone, properties.otpMaxPerPhonePerHour(), ONE_HOUR);
        findOtpEligible(phone).ifPresent(user -> {
            String code = oneTimeTokens.createLoginOtp(user.id());
            events.publishEvent(new SmsCodeRequestedEvent(user.phone(), SmsCodeRequestedEvent.Purpose.LOGIN, code, properties.otpTtl()));
        });
        return new CodeSentResponse(properties.otpTtl().toSeconds());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult verifyOtp(OtpVerifyRequest request, ClientInfo client) {
        requireOtpLoginEnabled();
        rateLimiter.check("otp-verify", client.ipAddress(), properties.rateLimits().otpVerify());
        UserCredentials user = findOtpEligible(PhoneNumbers.normalizeOrThrow(request.phone())).orElseThrow(AuthService::invalidCode);
        if (!oneTimeTokens.verifyLoginOtp(user.id(), request.code())) {
            throw invalidCode();
        }
        return startSession(user, client);
    }

    // ----- Registration and sessions -----

    /** Candidate self-registration: logs in right away and sends the email-confirmation link. */
    @Transactional
    public AuthResult register(RegisterRequest request, ClientInfo client) {
        AuthProperties.RateLimits limits = properties.rateLimits();
        rateLimiter.check("register", client.ipAddress(), limits.register());
        rateLimiter.check("register-email", normalizeEmail(request.email()), limits.registerPerIdentity());
        rateLimiter.check("register-phone", PhoneNumbers.normalizeOrThrow(request.phone()), limits.registerPerIdentity());
        UserCredentials user = users.registerCandidate(request.name(), request.email(), request.phone(), request.password());
        sendEmailVerification(user.id(), user.email(), user.name());
        return startSession(user, client);
    }

    /** New access token + rotated refresh token. Picks up role/company changes and deactivation. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String refreshToken, ClientInfo client) {
        Rotation rotation = refreshTokens.rotate(refreshToken, client.userAgent(), client.ipAddress());
        UserCredentials user = users.getCredentials(rotation.userId());
        if (!user.active()) {
            refreshTokens.revokeAllSessions(user.id());
            throw new UnauthorizedException("auth.session.expired");
        }
        IssuedAccessToken access = accessTokens.issue(user, rotation.next().familyId());
        return new AuthResult(new SessionResponse(users.getById(user.id()), access.value(), access.expiresAt()), rotation.next());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revokeSessionOf(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthenticatedUser actor) {
        return users.getById(actor.id());
    }

    // ----- Password, invitation and email flows -----

    /**
     * Always 204, whether the email has an account or not. The per-email limit counts every request,
     * so a 429 does not reveal whether the account exists either.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, ClientInfo client) {
        rateLimiter.check("forgot-password", client.ipAddress(), properties.rateLimits().forgotPassword());
        rateLimiter.check("forgot-password-email", normalizeEmail(request.email()), properties.rateLimits().forgotPasswordPerEmail());
        users.findCredentialsByEmail(request.email()).filter(UserCredentials::active).ifPresent(user -> {
            String token = oneTimeTokens.createPasswordResetToken(user.id());
            events.publishEvent(new PasswordResetRequestedEvent(user.email(), user.name(), token));
        });
    }

    /**
     * New password from the emailed link; ends every session of the user and cancels a pending email change
     * (the owner reacting to the "email change requested" notice).
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = oneTimeTokens.consumePasswordResetToken(request.token());
        users.setPassword(userId, request.password());
        users.markEmailVerified(userId);
        refreshTokens.revokeAllSessions(userId);
        oneTimeTokens.invalidatePending(userId, OneTimeTokenType.EMAIL_CHANGE);
        UserCredentials user = users.getCredentials(userId);
        events.publishEvent(new SecurityAlertEvent(user.email(), user.name(), SecurityAlertEvent.Kind.PASSWORD_RESET, clock.instant()));
    }

    /** First password of an invited user; confirms the email and logs in. */
    @Transactional
    public AuthResult acceptInvitation(AcceptInvitationRequest request, ClientInfo client) {
        UUID userId = oneTimeTokens.consumeInvitationToken(request.token());
        if (!users.getCredentials(userId).active()) {
            throw new BusinessException("auth.token.invalid");
        }
        users.setPassword(userId, request.password());
        users.markEmailVerified(userId);
        return startSession(users.getCredentials(userId), client);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        users.markEmailVerified(oneTimeTokens.consumeEmailVerificationToken(request.token()));
    }

    /**
     * Confirms a candidate's email change with the link sent to the new address. In one transaction:
     * {@code users.email} and {@code candidates.email} change, the email stays verified, the links sent to
     * the old address stop working, every other session ends (the caller's own session, identified by its
     * refresh cookie, is kept) and the old address gets a security alert.
     *
     * @param refreshToken the caller's refresh-token cookie, or {@code null}
     */
    @Transactional
    public void confirmEmailChange(ConfirmEmailChangeRequest request, String refreshToken) {
        EmailChangeTicket ticket = oneTimeTokens.consumeEmailChangeToken(request.token());
        UUID userId = ticket.userId();
        AppliedEmailChange applied = users.applyEmailChange(userId, ticket.newEmail());
        oneTimeTokens.invalidatePending(userId, OneTimeTokenType.PASSWORD_RESET, OneTimeTokenType.EMAIL_VERIFICATION, OneTimeTokenType.EMAIL_CHANGE);
        refreshTokens.revokeOtherSessions(userId, refreshTokens.sessionOf(refreshToken, userId).orElse(null));
        events.publishEvent(new SecurityAlertEvent(applied.previousEmail(), applied.name(), SecurityAlertEvent.Kind.EMAIL_CHANGED, clock.instant()));
    }

    @Transactional
    public void resendEmailVerification(AuthenticatedUser actor) {
        rateLimiter.check("email-verification-resend", actor.id().toString(), 1, properties.otpResendCooldown());
        UserResponse user = users.getById(actor.id());
        if (!user.emailVerified()) {
            sendEmailVerification(user.id(), user.email(), user.name());
        }
    }

    // ----- Helpers -----

    private AuthResult startSession(UserCredentials user, ClientInfo client) {
        users.registerSuccessfulLogin(user.id());
        IssuedRefreshToken refresh = refreshTokens.issueForNewSession(user.id(), client.userAgent(), client.ipAddress());
        IssuedAccessToken access = accessTokens.issue(user, refresh.familyId());
        return new AuthResult(new SessionResponse(users.getById(user.id()), access.value(), access.expiresAt()), refresh);
    }

    private void sendEmailVerification(UUID userId, String email, String name) {
        String token = oneTimeTokens.createEmailVerificationToken(userId);
        events.publishEvent(new EmailVerificationRequestedEvent(email, name, token));
    }

    /**
     * Accounts that may log in by SMS code: active, with an allowed role and without two-step
     * verification (an SMS code alone would bypass their password).
     */
    private Optional<UserCredentials> findOtpEligible(String phone) {
        return users.findCredentialsByPhone(phone)
                .filter(UserCredentials::active)
                .filter(user -> properties.otpLoginRoles().contains(user.role()))
                .filter(user -> !user.twoFactorEnabled());
    }

    /** Off unless enabled and SMS can be delivered (a real provider, or simulation in dev/test). */
    private void requireOtpLoginEnabled() {
        if (!properties.otpLoginEnabled() || !notifications.canDeliver(NotificationChannel.SMS)) {
            throw new NotFoundException("error.not-found");
        }
    }

    private String dummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            dummyPasswordHash = hash;
        }
        return hash;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static UnauthorizedException invalidLogin() {
        return new UnauthorizedException("auth.login.invalid");
    }

    private static UnauthorizedException invalidCode() {
        return new UnauthorizedException("auth.code.invalid");
    }
}
