package com.minhaempresa.recrutamentoapi.user.service;

import com.minhaempresa.recrutamentoapi.auth.dto.response.CodeSentResponse;
import com.minhaempresa.recrutamentoapi.auth.entity.OneTimeTokenType;
import com.minhaempresa.recrutamentoapi.auth.event.SmsCodeRequestedEvent;
import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties;
import com.minhaempresa.recrutamentoapi.auth.service.OneTimeTokenService;
import com.minhaempresa.recrutamentoapi.auth.service.RateLimiter;
import com.minhaempresa.recrutamentoapi.auth.service.RefreshTokenService;
import com.minhaempresa.recrutamentoapi.client.service.ClientService;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationChannel;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.shared.domain.PhoneNumbers;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.ForbiddenException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.user.dto.request.ChangePasswordRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.ChangePhoneRequest;
import com.minhaempresa.recrutamentoapi.user.dto.request.EmailChangeRequest;
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
import com.minhaempresa.recrutamentoapi.user.event.CandidateEmailVerifiedEvent;
import com.minhaempresa.recrutamentoapi.user.event.CandidateUserRegisteredEvent;
import com.minhaempresa.recrutamentoapi.user.event.EmailAddressInUseEvent;
import com.minhaempresa.recrutamentoapi.user.event.EmailChangeNoticeEvent;
import com.minhaempresa.recrutamentoapi.user.event.EmailChangeRequestedEvent;
import com.minhaempresa.recrutamentoapi.user.event.SecurityAlertEvent;
import com.minhaempresa.recrutamentoapi.user.event.UserInvitedEvent;
import com.minhaempresa.recrutamentoapi.user.mapper.UserMapper;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import com.minhaempresa.recrutamentoapi.user.repository.UserSpecifications;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User accounts: administration (staff and client users), own profile, and the account operations the
 * auth module needs (credentials, login counters, passwords).
 *
 * <p>Rules from the frontend ({@code DECISOES.md} #90): a staff or client email cannot change (candidates
 * change theirs only through a confirmed link, stage 7b), an administrator cannot
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
    private final ClientService clientService;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<AccountEmailContributor> emailContributors;
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
        if (request.role() == UserRole.CLIENT) {
            requireActiveClient(request.clientId());
        }
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
        if (clientId != null && !clientId.equals(user.getClientId())) {
            requireActiveClient(clientId);
        }
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

    /**
     * A candidate asks to change the login email (staff emails never change). Needs the current password
     * (same attempt limit as the other sensitive changes). Always the same outcome for the caller: the
     * current address is told a change was asked; a free new address gets the confirmation link (24 h), an
     * address already used by an account or by a candidate without an account only gets a notice and
     * nothing changes (no enumeration, no merged profiles). A new request replaces the pending one.
     */
    @Transactional
    public void requestEmailChange(AuthenticatedUser actor, EmailChangeRequest request) {
        User user = find(actor.id());
        if (user.getRole() != UserRole.CANDIDATE) {
            throw new ForbiddenException();
        }
        confirmPassword(user, request.currentPassword(), "currentPassword");
        String newEmail = normalizeEmail(request.newEmail());
        if (newEmail.equals(user.getEmail())) {
            throw new BusinessException("account.email.same", List.of(new FieldError("newEmail", "account.email.same")));
        }
        AuthProperties.Limit limit = authProperties.rateLimits().emailChange();
        rateLimiter.check("email-change", user.getId().toString(), limit);
        rateLimiter.check("email-change-address", newEmail, limit);

        oneTimeTokens.invalidatePending(user.getId(), OneTimeTokenType.EMAIL_CHANGE);
        events.publishEvent(new EmailChangeNoticeEvent(user.getEmail(), user.getName(), clock.instant()));
        if (isEmailTaken(newEmail, user.getId())) {
            events.publishEvent(new EmailAddressInUseEvent(newEmail));
            return;
        }
        String token = oneTimeTokens.createEmailChangeToken(user.getId(), newEmail);
        events.publishEvent(new EmailChangeRequestedEvent(newEmail, user.getName(), token));
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
        return repository.findByEmail(normalizeEmail(email)).map(this::credentials);
    }

    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByPhone(String phone) {
        return PhoneNumbers.normalize(phone).flatMap(repository::findByPhone).map(this::credentials);
    }

    @Transactional(readOnly = true)
    public UserCredentials getCredentials(UUID id) {
        return credentials(find(id));
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

    /**
     * Following a link sent to the address proves the user owns it. For a candidate without a profile
     * yet, {@link CandidateEmailVerifiedEvent} lets the candidates module create or link it in this
     * transaction.
     */
    @Transactional
    public void markEmailVerified(UUID id) {
        User user = find(id);
        user.markEmailVerified(clock.instant());
        if (user.getRole() == UserRole.CANDIDATE && user.getCandidateId() == null) {
            events.publishEvent(new CandidateEmailVerifiedEvent(user.getId(), user.getEmail(), user.getName(), user.getPhone()));
        }
    }

    /** Result of a confirmed email change: the address it replaced (for the security alert). */
    public record AppliedEmailChange(String previousEmail, String name) {}

    /**
     * Applies a confirmed email change ({@code newEmail} lower case, from the token): {@code users.email} and
     * every copy kept by other modules ({@link AccountEmailContributor}) change together, and the email
     * stays verified (the link proved the new address). An account whose email was never confirmed gets its
     * candidate profile now, with the new address.
     *
     * <p>Two confirmations for the same address at the same time: the unique constraints let only one win;
     * the other answers 422 {@code account.email.taken} and nothing changes.
     */
    @Transactional
    public AppliedEmailChange applyEmailChange(UUID userId, String newEmail) {
        User user = find(userId);
        if (user.getRole() != UserRole.CANDIDATE || !user.isActive()) {
            throw new BusinessException("auth.token.invalid");
        }
        if (isEmailTaken(newEmail, userId)) {
            throw new BusinessException("account.email.taken");
        }
        String previous = user.getEmail();
        try {
            user.changeEmail(newEmail);
            emailContributors.orderedStream().forEach(contributor -> contributor.onAccountEmailChanged(userId, newEmail));
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("account.email.taken");
        }
        markEmailVerified(userId);
        return new AppliedEmailChange(previous, user.getName());
    }

    // =====================================================================================
    // Support for other modules (never exposed by a controller)
    // =====================================================================================

    /** The account of a candidate user ({@code empty} for other roles or unknown ids). */
    @Transactional(readOnly = true)
    public Optional<CandidateAccount> findCandidateAccount(UUID userId) {
        return repository.findById(userId)
                .filter(u -> u.getRole() == UserRole.CANDIDATE)
                .map(u -> new CandidateAccount(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getEmailVerifiedAt() != null, u.getCandidateId()));
    }

    /** Links the candidate profile to its account (the access token carries it from the next refresh). */
    @Transactional
    public void linkCandidate(UUID userId, UUID candidateId) {
        find(userId).setCandidateId(candidateId);
    }

    /** Names of the given users (missing ids are absent). */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(Set.copyOf(ids)).stream().collect(Collectors.toMap(User::getId, User::getName));
    }

    /** Name, role and active flag of the given users (missing ids are absent). */
    @Transactional(readOnly = true)
    public Map<UUID, UserReference> referencesOf(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(Set.copyOf(ids)).stream()
                .collect(Collectors.toMap(User::getId, u -> new UserReference(u.getId(), u.getName(), u.getRole(), u.isActive())));
    }

    /** Active administrators (they receive the emails about new requests). */
    @Transactional(readOnly = true)
    public List<UserContact> activeAdministrators() {
        return repository.findByRole(UserRole.ADMIN).stream()
                .filter(User::isActive)
                .map(u -> new UserContact(u.getId(), u.getName(), u.getEmail()))
                .toList();
    }

    /** Portal users of a client company who can still log in (inactive company or user → none). */
    @Transactional(readOnly = true)
    public List<UserContact> activeContactsOfClient(UUID clientId) {
        return repository.findByClientId(clientId).stream()
                .filter(u -> u.getRole() == UserRole.CLIENT && credentials(u).active())
                .map(u -> new UserContact(u.getId(), u.getName(), u.getEmail()))
                .toList();
    }

    /** Contact of a user who can still log in (for client users: with an active company). */
    @Transactional(readOnly = true)
    public Optional<UserContact> findActiveContact(UUID id) {
        return repository.findById(id)
                .filter(u -> credentials(u).active())
                .map(u -> new UserContact(u.getId(), u.getName(), u.getEmail()));
    }

    /** Ends every session of the portal users of a deactivated client company. */
    @Transactional
    public void revokeSessionsOfClient(UUID clientId) {
        repository.findByClientId(clientId).forEach(u -> refreshTokens.revokeAllSessions(u.getId()));
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

    /** Used by another account, or by a record of another module that is not this user's (candidates). */
    private boolean isEmailTaken(String email, UUID userId) {
        return repository.existsByEmail(email)
                || emailContributors.orderedStream().anyMatch(contributor -> contributor.isEmailUsedByOthers(email, userId));
    }

    private void publishAlert(User user, SecurityAlertEvent.Kind kind) {
        events.publishEvent(new SecurityAlertEvent(user.getEmail(), user.getName(), kind, clock.instant()));
    }

    /**
     * Login view of the account. A client user of an inactive company counts as inactive (no login,
     * no refresh, no password reset) without changing the user's own flag, so reactivating the company
     * gives access back to the users that were not deactivated individually.
     */
    private UserCredentials credentials(User user) {
        UserCredentials credentials = mapper.toCredentials(user);
        if (credentials.active() && user.getRole() == UserRole.CLIENT && !clientService.isActive(user.getClientId())) {
            return credentials.asInactive();
        }
        return credentials;
    }

    private void requireActiveClient(UUID clientId) {
        if (!clientService.isActive(clientId)) {
            throw new BusinessException("user.client-inactive", List.of(new FieldError("clientId", "user.client-inactive")));
        }
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
