package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailAccount;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.entity.EmailSecurity;
import com.minhaempresa.recrutamentoapi.notification.repository.EmailAccountRepository;
import com.minhaempresa.recrutamentoapi.notification.repository.EmailRouteRepository;
import com.minhaempresa.recrutamentoapi.notification.sender.MailHeaders;
import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import com.minhaempresa.recrutamentoapi.shared.crypto.SecretCipher;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * The SMTP connection and sender of each purpose. The route is read from the database on every send, so
 * a change in the settings applies to the next email without a restart (also with several instances);
 * the built {@link JavaMailSenderImpl} is reused while the account is unchanged (id + {@code updatedAt}).
 * No route, or an inactive account → the default server ({@code spring.mail}) and {@code app.mail.from}.
 */
@Component
@RequiredArgsConstructor
public class MailSenderRegistry {

    static final int TIMEOUT_MILLIS = 10_000;

    private final EmailRouteRepository routes;
    private final EmailAccountRepository accounts;
    private final ObjectProvider<JavaMailSender> defaultSender;
    private final MailSenderProperties properties;
    private final SecretCipher cipher;
    private final SmtpHostPolicy hostPolicy;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    /** What sends an email: the connection, the sender and the optional {@code Reply-To}. */
    public record ResolvedSender(JavaMailSender sender, InternetAddress from, InternetAddress replyTo) {}

    private record Cached(Instant version, JavaMailSenderImpl sender) {}

    /**
     * Not {@code @Transactional} on purpose: the outbox worker calls it inside its own transaction, and an
     * exception leaving a transactional proxy (blocked host, undecryptable password) would mark that
     * transaction rollback-only and lose the record of the failed attempt.
     */
    public ResolvedSender forPurpose(EmailPurpose purpose) {
        Optional<EmailAccount> account = routes.findByPurpose(purpose)
                .flatMap(route -> accounts.findById(route.getAccountId()))
                .filter(EmailAccount::isActive);
        if (account.isPresent()) {
            return resolve(account.get());
        }
        JavaMailSender fallback = defaultSender.getIfAvailable();
        if (fallback == null) {
            throw new IllegalStateException("No SMTP account for " + purpose + " and no spring.mail server configured");
        }
        return new ResolvedSender(fallback, MailHeaders.parse(properties.from()), null);
    }

    private ResolvedSender resolve(EmailAccount account) {
        // Again before connecting: the name may now resolve to an internal address
        hostPolicy.requireAllowed(account.getHost());
        Cached cached = cache.compute(account.getId(), (id, current) -> current != null && current.version().equals(account.getUpdatedAt())
                ? current
                : new Cached(account.getUpdatedAt(), build(account.getHost(), account.getPort(), account.getSecurity(), account.getUsername(),
                        account.getPasswordEncrypted() == null ? null : cipher.decrypt(account.getPasswordEncrypted()))));
        return new ResolvedSender(
                cached.sender(),
                MailHeaders.address(account.getFromAddress(), account.getFromName()),
                account.getReplyTo() == null ? null : MailHeaders.address(account.getReplyTo(), null));
    }

    /** A connection with short timeouts; TLS is required (not optional) for {@code starttls} and {@code ssl}. */
    static JavaMailSenderImpl build(String host, int port, EmailSecurity security, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
        props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MILLIS));
        props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MILLIS));
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
            props.put("mail.smtp.auth", "true");
        }
        switch (security) {
            case STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case SSL -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
                // Plain connection (only sensible for a relay on a trusted network)
            }
        }
        return sender;
    }
}
