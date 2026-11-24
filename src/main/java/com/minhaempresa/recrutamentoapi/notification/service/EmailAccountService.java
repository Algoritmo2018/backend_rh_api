package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.dto.request.EmailAccountRequest;
import com.minhaempresa.recrutamentoapi.notification.dto.request.EmailAccountTestRequest;
import com.minhaempresa.recrutamentoapi.notification.dto.request.EmailRouteRequest;
import com.minhaempresa.recrutamentoapi.notification.dto.response.EmailAccountResponse;
import com.minhaempresa.recrutamentoapi.notification.dto.response.EmailRouteResponse;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailAccount;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailRoute;
import com.minhaempresa.recrutamentoapi.notification.mapper.NotificationMapper;
import com.minhaempresa.recrutamentoapi.notification.repository.EmailAccountRepository;
import com.minhaempresa.recrutamentoapi.notification.repository.EmailRouteRepository;
import com.minhaempresa.recrutamentoapi.notification.sender.MailHeaders;
import com.minhaempresa.recrutamentoapi.shared.crypto.SecretCipher;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import com.minhaempresa.recrutamentoapi.shared.exception.ConflictException;
import com.minhaempresa.recrutamentoapi.shared.exception.NotFoundException;
import com.minhaempresa.recrutamentoapi.shared.i18n.Messages;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.text.Texts;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMTP accounts and the account of each kind of email (settings tab "Emails de envio", administrators).
 * Passwords are encrypted before saving ({@link SecretCipher}) and never returned; a blank password
 * keeps the stored one. Hosts must be public servers in production ({@link SmtpHostPolicy}). Changes
 * apply to the next email (no restart).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAccountService {

    private final EmailAccountRepository accounts;
    private final EmailRouteRepository routes;
    private final NotificationMapper mapper;
    private final SecretCipher cipher;
    private final SmtpHostPolicy hostPolicy;
    private final UserService userService;
    private final Messages messages;

    // =====================================================================================
    // Accounts
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<EmailAccountResponse> list() {
        return accounts.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public EmailAccountResponse create(EmailAccountRequest request) {
        if (accounts.existsByNameIgnoreCase(request.name().trim())) {
            throw nameTaken();
        }
        EmailAccount account = new EmailAccount();
        apply(account, request);
        return mapper.toResponse(accounts.saveAndFlush(account));
    }

    /** Replaces the account; a blank password keeps the stored one. */
    @Transactional
    public EmailAccountResponse update(UUID id, EmailAccountRequest request) {
        EmailAccount account = find(id);
        if (accounts.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw nameTaken();
        }
        apply(account, request);
        return mapper.toResponse(accounts.saveAndFlush(account));
    }

    /** An account used by a kind of email cannot be deleted (change the route first, or deactivate it). */
    @Transactional
    public void delete(UUID id) {
        EmailAccount account = find(id);
        if (routes.existsByAccountId(id)) {
            throw new BusinessException("settings.email-account.in-use");
        }
        accounts.delete(account);
    }

    // =====================================================================================
    // Routes (which account sends each kind of email)
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<EmailRouteResponse> listRoutes() {
        Map<EmailPurpose, EmailRoute> byPurpose = new HashMap<>();
        routes.findAll().forEach(route -> byPurpose.put(route.getPurpose(), route));
        Map<UUID, String> names = new HashMap<>();
        accounts.findAll().forEach(account -> names.put(account.getId(), account.getName()));
        return Arrays.stream(EmailPurpose.values())
                .map(purpose -> {
                    EmailRoute route = byPurpose.get(purpose);
                    return route == null
                            ? new EmailRouteResponse(purpose, null, null)
                            : new EmailRouteResponse(purpose, route.getAccountId(), names.get(route.getAccountId()));
                })
                .toList();
    }

    /** Sets the account of the given kinds of email ({@code accountId: null} = default server). */
    @Transactional
    public List<EmailRouteResponse> updateRoutes(List<EmailRouteRequest> requests) {
        for (int i = 0; i < requests.size(); i++) {
            EmailRouteRequest request = requests.get(i);
            EmailRoute existing = routes.findByPurpose(request.purpose()).orElse(null);
            if (request.accountId() == null) {
                if (existing != null) {
                    routes.delete(existing);
                }
                continue;
            }
            if (!accounts.existsById(request.accountId())) {
                throw new BusinessException("settings.email-account.not-found", List.of(new FieldError("[" + i + "].accountId", "settings.email-account.not-found")));
            }
            if (existing == null) {
                routes.save(new EmailRoute(request.purpose(), request.accountId()));
            } else {
                existing.setAccountId(request.accountId());
            }
        }
        routes.flush();
        return listRoutes();
    }

    // =====================================================================================
    // Test email
    // =====================================================================================

    /**
     * Sends a test email with the data of the form, only to the logged-in administrator (never to an
     * address chosen in the request). Errors are reported in general terms: the answer never repeats the
     * server message (it could reveal internal hosts or addresses).
     */
    @Transactional(readOnly = true)
    public void sendTest(EmailAccountTestRequest request, AuthenticatedUser actor) {
        EmailAccountRequest form = request.account();
        hostPolicy.requireAllowed(form.host().trim());
        String password = Texts.blankToNull(form.password());
        if (password == null && request.accountId() != null) {
            EmailAccount saved = find(request.accountId());
            password = saved.getPasswordEncrypted() == null ? null : cipher.decrypt(saved.getPasswordEncrypted());
        }
        String recipient = userService.getById(actor.id()).email();
        JavaMailSenderImpl sender = MailSenderRegistry.build(form.host().trim(), form.port(), form.security(), Texts.blankToNull(form.username()), password);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(MailHeaders.address(form.fromAddress(), form.fromName()));
            helper.setTo(MailHeaders.address(recipient, null));
            if (Texts.blankToNull(form.replyTo()) != null) {
                helper.setReplyTo(MailHeaders.address(form.replyTo(), null));
            }
            helper.setSubject(MailHeaders.clean(messages.get("email.test.subject")));
            helper.setText(messages.get("email.test.body", MailHeaders.clean(form.name())));
            sender.send(message);
        } catch (MailAuthenticationException e) {
            throw testFailed("settings.email-account.test.auth");
        } catch (MailException e) {
            throw testFailed(isAuthenticationFailure(e) ? "settings.email-account.test.auth" : "settings.email-account.test.connection");
        } catch (jakarta.mail.MessagingException | IllegalArgumentException e) {
            throw testFailed("settings.email-account.test.failed");
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private void apply(EmailAccount account, EmailAccountRequest request) {
        String host = request.host().trim().toLowerCase(Locale.ROOT);
        hostPolicy.requireAllowed(host);
        account.setName(request.name().trim());
        account.setHost(host);
        account.setPort(request.port());
        account.setSecurity(request.security());
        account.setUsername(Texts.blankToNull(request.username()));
        String password = Texts.blankToNull(request.password());
        if (password != null) {
            if (!cipher.isConfigured()) {
                throw new BusinessException("settings.email-account.secrets-key-missing", List.of(new FieldError("password", "settings.email-account.secrets-key-missing")));
            }
            account.setPasswordEncrypted(cipher.encrypt(password));
        }
        if (account.getUsername() == null) {
            // No authentication: no password to keep
            account.setPasswordEncrypted(null);
        }
        account.setFromAddress(request.fromAddress().trim().toLowerCase(Locale.ROOT));
        account.setFromName(MailHeaders.clean(Texts.blankToNull(request.fromName())));
        String replyTo = Texts.blankToNull(request.replyTo());
        account.setReplyTo(replyTo == null ? null : replyTo.toLowerCase(Locale.ROOT));
        account.setActive(request.active() == null || request.active());
    }

    private static boolean isAuthenticationFailure(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof AuthenticationFailedException) {
                return true;
            }
        }
        return false;
    }

    private EmailAccount find(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("settings.email-account.not-found"));
    }

    private static BusinessException testFailed(String key) {
        return new BusinessException(key);
    }

    private static ConflictException nameTaken() {
        return new ConflictException("settings.email-account.name-taken", List.of(new FieldError("name", "settings.email-account.name-taken")));
    }
}
