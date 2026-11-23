package com.minhaempresa.recrutamentoapi.user.service;

import com.minhaempresa.recrutamentoapi.auth.dto.response.CodeSentResponse;
import com.minhaempresa.recrutamentoapi.auth.event.SmsCodeRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties;
import com.minhaempresa.recrutamentoapi.auth.service.OneTimeTokenService;
import com.minhaempresa.recrutamentoapi.auth.service.RateLimiter;
import com.minhaempresa.recrutamentoapi.auth.service.RefreshTokenService;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationChannel;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.dto.request.ChangePasswordRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.ChangePhoneRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.PhoneCodeRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.TwoFactorRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UpdateMyProfileRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserCreateRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserFilterRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserStatusRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.UserUpdateRequest;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserResponse;
import com.minhaempresa.recrutamentoapi.user.dto.response.UserSummaryResponse;
import com.minhaempresa.recrutamentoapi.user.entity.User;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.event.CandidateUserRegisteredEvent;
import com.minhaempresa.recrutamentoapi.user.event.SecurityAlertEvent;
import com.minhaempresa.recrutamentoapi.user.event.UserInvitedEvent;
import com.minhaempresa.recrutamentoapi.user.mapper.UserMapper;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import com.minhaempresa.recrutamentoapi.user.repository.UserSpecifications;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User accounts: administration (staff and client users), own profile, and the account operations the
 * auth module needs (credentials, login counters, passwords).
 *
 * <p>Rules from the frontend ({@code DECISOES.md} #90): the email cannot change, an administrator cannot
 * change their own role or deactivate themselves, a client user needs a company, candidates are not
 * managed here and users are never deleted (they are deactivated).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Sort BY_NAME = Sort.by("name");

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokens;
    private final RefreshTokenService refreshTokens;
    private final RateLimiter rateLimiter;
    private final NotificationDispatcher notifications;
    private final AuthProperties authProperties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // =====================================================================================
    // Administration
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<UserResponse> list(UserFilterRequest filter) {
        Set<UserRole> roles = filter.roles() == null || filter.roles().isEmpty()
                ? EnumSet.complementOf(EnumSet.of(UserRole.CANDIDATE))
                : EnumSet.copyOf(filter.roles());
        return repository.findAll(UserSpecifications.roleIn(roles).and(UserSpecifications.clientId(filter.clientId())), BY_NAME)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Internal staff for pickers, without contact details. */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listInternal() {
        return repository.findByRoleIn(UserRole.INTERNAL, BY_NAME).stream()
                .filter(User::isActive)
                .map(mapper::toSummary)
                .toList();
    }

    /** Creates a staff or client user and sends the invitation email. */
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        requireManagedRole(request.role());
        requireClientForClientRole(request.role(), request.clientId());
        String email = normalizeEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw new ConflictException("user.email-taken", List.of(new FieldError("email", "user.email-taken")));
        }
        String phone = PhoneNumbers.normalizeOrThrow(request.phone());
        requirePhoneAvailable(phone, null);

        User user = new User(request.name().trim(), email, phone, request.role());
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setClientId(request.role() == UserRole.CLIENT ? request.clientId() : null);
        repository.save(user);
        invite(user);
        return mapper.toResponse(user);
    }

    /**
     * Edits a user. A change of role or company revokes the user's sessions (the current access token
     * stays valid until it expires).
     */
    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request, AuthenticatedUser actor) {
        User user = find(id);
        if (request.email() != null && !normalizeEmail(request.email()).equals(user.getEmail())) {
            throw new BusinessException("user.email-immutable", List.of(new FieldError("email", "user.email-immutable")));
        }
        if (request.role() != user.getRole()) {
            if (user.getId().equals(actor.id())) {
                throw new BusinessException("user.own-role", List.of(new FieldError("role", "user.own-role")));
            }
            requireManagedRole(request.role());
            requireManagedRole(user.getRole());
        }
        requireClientForClientRole(request.role(), request.clientId());
        String phone = PhoneNumbers.normalizeOrThrow(request.phone());
        requirePhoneAvailable(phone, user.getId());

        UUID clientId = request.role() == UserRole.CLIENT ? request.clientId() : null;
        boolean claimsChanged = request.role() != user.getRole() || !Objects.equals(clientId, user.getClientId());
        user.setName(request.name().trim());
        user.setPhone(phone);
        user.setRole(request.role());
        user.setJobTitle(blankToNull(request.jobTitle()));
        user.setClientId(clientId);
        if (claimsChanged) {
            refreshTokens.revokeAllSessions(user.getId());
        }
        return mapper.toResponse(user);
    }

    @Transactional
    public void resendInvitation(UUID id) {
        User user = find(id);
        if (user.hasPassword()) {
            throw new BusinessException("user.invitation.already-accepted");
        }
        invite(user);
    }

    /** Deactivating revokes every session at once; the current access token expires within minutes. */
    @Transactional
    public UserResponse setStatus(UUID id, UserStatusRequest request, AuthenticatedUser actor) {
        User user = find(id);
        if (!request.active() && user.getId().equals(actor.id())) {
            throw new BusinessException("user.own-status");
        }
        user.setActive(request.active());
        if (!request.active()) {
            refreshTokens.revokeAllSessions(user.getId());
        }
        return mapper.toResponse(user);
    }

    // =====================================================================================
    // Own account (/me)
    // =====================================================================================

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return mapper.toResponse(find(id));
    }

    /** Name and job title; the phone has its own flow ({@link #changeMyPhone}). */
    @Transactional
    public UserResponse updateMyProfile(AuthenticatedUser actor, UpdateMyProfileRequest request) {
        User user = find(actor.id());
        user.setName(request.name().trim());
        user.setJobTitle(blankToNull(request.jobTitle()));
        return mapper.toResponse(user);
    }

    /** Changes the password and ends every other session (the current one, {@code sid}, stays). */
    @Transactional
    public void changeMyPassword(AuthenticatedUser actor, ChangePasswordRequest request) {
        User user = find(actor.id());
        confirmPassword(user, request.current(), "current");
        if (request.next().equals(request.current())) {
            throw new BusinessException("account.password.same", List.of(new FieldError("next", "account.password.same")));
        }
        user.changePassword(passwordEncoder.encode(request.next()));
        refreshTokens.revokeOtherSessions(user.getId(), actor.sessionId());
        publishAlert(user, SecurityAlertEvent.Kind.PASSWORD_CHANGED);
    }

    /**
     * Sends a code to the new number. Only required for accounts with two-step verification, so that
     * the second factor never moves to a number the user does not control.
     */
    @Transactional
    public CodeSentResponse requestPhoneChangeCode(AuthenticatedUser actor, PhoneCodeRequest request) {
        requireSmsAvailable();
        User user = find(actor.id());
        String phone = PhoneNumbers.normalizeOrThrow(request.phone());
        requireNewPhone(user, phone);
        requirePhoneAvailable(phone, user.getId());
        rateLimiter.check("phone-change-code", user.getId().toString(), 1, authProperties.otpResendCooldown());
        rateLimiter.check("otp-phone-hourly", phone, authProperties.otpMaxPerPhonePerHour(), Duration.ofHours(1));
        String code = oneTimeTokens.createPhoneChangeCode(user.getId(), phone);
        events.publishEvent(new SmsCodeRequestedEvent(phone, SmsCodeRequestedEvent.Purpose.PHONE_CHANGE, code, authProperties.otpTtl()));
        return new CodeSentResponse(authProperties.otpTtl().toSeconds());
    }

    /**
     * Changes the own phone: current password always; with two-step verification on, also the code
     * sent to the new number (a wrong code still counts as an attempt).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UserResponse changeMyPhone(AuthenticatedUser actor, ChangePhoneRequest request) {
        User user = find(actor.id());
        String phone = PhoneNumbers.normalizeOrThrow(request.phone());
        requireNewPhone(user, phone);
        confirmPassword(user, request.currentPassword(), "currentPassword");
        if (user.isTwoFactorEnabled()) {
            if (request.code() == null) {
                throw new BusinessException("account.phone.code-required", List.of(new FieldError("code", "account.phone.code-required")));
            }
            if (!oneTimeTokens.verifyPhoneChangeCode(user.getId(), phone, request.code())) {
                throw new BusinessException("auth.code.invalid", List.of(new FieldError("code", "auth.code.invalid")));
            }
        }
        requirePhoneAvailable(phone, user.getId());
        user.setPhone(phone);
        publishAlert(user, SecurityAlertEvent.Kind.PHONE_CHANGED);
        return mapper.toResponse(user);
    }

    /** Sends the SMS code needed to enable two-step verification. */
    @Transactional
    public CodeSentResponse requestTwoFactorCode(AuthenticatedUser actor) {
        requireSmsAvailable();
        rateLimiter.check("two-factor-code", actor.id().toString(), 1, authProperties.otpResendCooldown());
        User user = find(actor.id());
        String code = oneTimeTokens.createTwoFactorSetupCode(user.getId());
        events.publishEvent(new SmsCodeRequestedEvent(user.getPhone(), SmsCodeRequestedEvent.Purpose.TWO_FACTOR, code, authProperties.otpTtl()));
        return new CodeSentResponse(authProperties.otpTtl().toSeconds());
    }

    /**
     * Enables two-step verification (with the SMS code) or disables it (with the current password). A
     * wrong code still counts as an attempt.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UserResponse setMyTwoFactor(AuthenticatedUser actor, TwoFactorRequest request) {
        User user = find(actor.id());
        if (request.enabled() == user.isTwoFactorEnabled()) {
            return mapper.toResponse(user);
        }
        if (request.enabled()) {
            requireSmsAvailable();
            if (request.code() == null) {
                throw new BusinessException("auth.two-factor.code-required", List.of(new FieldError("code", "auth.two-factor.code-required")));
            }
            if (!oneTimeTokens.verifyTwoFactorSetupCode(user.getId(), request.code())) {
                throw new BusinessException("auth.code.invalid", List.of(new FieldError("code", "auth.code.invalid")));
            }
        } else {
            confirmPassword(user, request.currentPassword(), "currentPassword");
        }
        user.setTwoFactorEnabled(request.enabled());
        publishAlert(user, request.enabled() ? SecurityAlertEvent.Kind.TWO_FACTOR_ENABLED : SecurityAlertEvent.Kind.TWO_FACTOR_DISABLED);
        return mapper.toResponse(user);
    }

    // =====================================================================================
    // Support for the auth module (never exposed by a controller)
    // =====================================================================================

    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return repository.findByEmail(normalizeEmail(email)).map(mapper::toCredentials);
    }

    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByPhone(String phone) {
        return PhoneNumbers.normalize(phone).flatMap(repository::findByPhone).map(mapper::toCredentials);
    }

    @Transactional(readOnly = true)
    public UserCredentials getCredentials(UUID id) {
        return mapper.toCredentials(find(id));
    }

    @Transactional
    public void registerFailedLogin(UUID id) {
        find(id).registerFailedLogin(authProperties.maxFailedLogins(), authProperties.lockoutDuration(), clock.instant());
    }

    @Transactional
    public void registerSuccessfulLogin(UUID id) {
        find(id).registerSuccessfulLogin(clock.instant());
    }

    /** Candidate self-registration; the candidate profile is created by the candidates module (event). */
    @Transactional
    public UserCredentials registerCandidate(String name, String email, String phone, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (repository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("auth.register.email-taken", List.of(new FieldError("email", "auth.register.email-taken")));
        }
        String normalizedPhone = PhoneNumbers.normalizeOrThrow(phone);
        if (repository.existsByPhone(normalizedPhone)) {
            throw new ConflictException("auth.register.phone-taken", List.of(new FieldError("phone", "auth.register.phone-taken")));
        }
        User user = new User(name.trim(), normalizedEmail, normalizedPhone, UserRole.CANDIDATE);
        user.changePassword(passwordEncoder.encode(rawPassword));
        user.setTermsAcceptedAt(clock.instant());
        repository.save(user);
        events.publishEvent(new CandidateUserRegisteredEvent(user.getId(), user.getEmail(), user.getName(), user.getPhone()));
        return mapper.toCredentials(user);
    }

    /** Sets a new password (reset or accepted invitation); also lifts a lockout. */
    @Transactional
    public void setPassword(UUID id, String rawPassword) {
        find(id).changePassword(passwordEncoder.encode(rawPassword));
    }

    /** Following a link sent to the address proves the user owns it. */
    @Transactional
    public void markEmailVerified(UUID id) {
        find(id).markEmailVerified(clock.instant());
    }

    // =====================================================================================
    // First administrator
    // =====================================================================================

    /**
     * Creates the configured administrator if there is no administrator yet, or re-sends the invitation
     * if that administrator never set a password and the last invitation has expired.
     */
    @Transactional
    public void bootstrapAdmin(BootstrapAdminProperties properties) {
        if (!properties.isConfigured() || repository.existsByRoleAndPasswordHashIsNotNull(UserRole.ADMIN)) {
            return;
        }
        String email = normalizeEmail(properties.email());
        Optional<User> existing = repository.findByEmail(email);
        if (existing.isPresent()) {
            User admin = existing.get();
            if (admin.getRole() == UserRole.ADMIN && !admin.hasPassword() && !oneTimeTokens.hasActiveInvitation(admin.getId())) {
                invite(admin);
                log.info("Bootstrap administrator {} has not accepted the invitation yet: invitation re-sent", email);
            }
            return;
        }
        if (!repository.findByRole(UserRole.ADMIN).isEmpty()) {
            return;
        }
        String phone = PhoneNumbers.normalize(properties.phone())
                .orElseThrow(() -> new IllegalStateException("app.bootstrap.admin.phone must be an Angolan mobile number"));
        String name = properties.name() == null || properties.name().isBlank() ? "Administrador" : properties.name().trim();
        User admin = repository.save(new User(name, email, phone, UserRole.ADMIN));
        invite(admin);
        log.info("Bootstrap administrator {} created: invitation sent", email);
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /**
     * Checks the current password of a sensitive operation (limited per user, so a stolen session
     * cannot be used to guess it). Wrong or missing → 422 on {@code field}.
     */
    private void confirmPassword(User user, String rawPassword, String field) {
        rateLimiter.check("password-confirmation", user.getId().toString(), authProperties.rateLimits().passwordConfirmation());
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException("account.password.current-required", List.of(new FieldError(field, "account.password.current-required")));
        }
        if (!user.hasPassword() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException("account.password.current-invalid", List.of(new FieldError(field, "account.password.current-invalid")));
        }
    }

    private static void requireNewPhone(User user, String phone) {
        if (phone.equals(user.getPhone())) {
            throw new BusinessException("account.phone.same", List.of(new FieldError("phone", "account.phone.same")));
        }
    }

    private void publishAlert(User user, SecurityAlertEvent.Kind kind) {
        events.publishEvent(new SecurityAlertEvent(user.getEmail(), user.getName(), kind, clock.instant()));
    }

    private void invite(User user) {
        String token = oneTimeTokens.createInvitationToken(user.getId());
        events.publishEvent(new UserInvitedEvent(user.getEmail(), user.getName(), token));
    }

    private User find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("user.not-found"));
    }

    private void requirePhoneAvailable(String phone, UUID ownId) {
        boolean taken = ownId == null ? repository.existsByPhone(phone) : repository.existsByPhoneAndIdNot(phone, ownId);
        if (taken) {
            throw new ConflictException("user.phone-taken", List.of(new FieldError("phone", "user.phone-taken")));
        }
    }

    private void requireSmsAvailable() {
        if (!notifications.canDeliver(NotificationChannel.SMS)) {
            throw new BusinessException("auth.two-factor.unavailable");
        }
    }

    private static void requireManagedRole(UserRole role) {
        if (role == UserRole.CANDIDATE) {
            throw new BusinessException("user.candidate-not-managed", List.of(new FieldError("role", "user.candidate-not-managed")));
        }
    }

    private static void requireClientForClientRole(UserRole role, UUID clientId) {
        if (role == UserRole.CLIENT && clientId == null) {
            throw new BusinessException("user.client-required", List.of(new FieldError("clientId", "user.client-required")));
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
