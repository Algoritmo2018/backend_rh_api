package com.minhaempresa.recrutamentoapi.support;

import com.minhaempresa.recrutamentoapi.auth.service.AccessTokenService;
import com.minhaempresa.recrutamentoapi.user.entity.User;
import com.minhaempresa.recrutamentoapi.user.entity.UserRole;
import com.minhaempresa.recrutamentoapi.user.repository.UserRepository;
import com.minhaempresa.recrutamentoapi.user.service.UserService;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Creates users directly in the database (unique email and phone per call) and access tokens for
 * them. Every test creates its own users, so tests never depend on each other.
 */
public class TestUsers {

    /** Password of every user created here (satisfies the password policy). */
    public static final String PASSWORD = "Senha12345";

    private static final AtomicInteger SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1_000_000, 9_000_000));

    private final UserRepository repository;
    private final UserService userService;
    private final AccessTokenService accessTokens;
    private final String passwordHash;

    public TestUsers(UserRepository repository, UserService userService, AccessTokenService accessTokens, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.userService = userService;
        this.accessTokens = accessTokens;
        // BCrypt is deliberately slow: hash once and reuse
        this.passwordHash = passwordEncoder.encode(PASSWORD);
    }

    /** Created user: id, login email and E.164 phone. */
    public record TestUser(UUID id, String email, String phone, UserRole role) {}

    public TestUser create(UserRole role) {
        return create(role, true);
    }

    public TestUser create(UserRole role, boolean withPassword) {
        int n = SEQUENCE.incrementAndGet();
        String email = "user-" + n + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.ao";
        String phone = "+244" + uniqueLocalPhone();
        User user = new User("Utilizador Teste " + n, email, phone, role);
        if (withPassword) {
            user.changePassword(passwordHash);
        }
        if (role == UserRole.CLIENT) {
            user.setClientId(UUID.randomUUID());
        }
        repository.save(user);
        return new TestUser(user.getId(), email, phone, role);
    }

    /** Access token for the user, as if issued at login (new session id). */
    public String accessToken(TestUser user) {
        return accessTokens.issue(userService.getCredentials(user.id()), UUID.randomUUID()).value();
    }

    /** {@code Authorization: Bearer …} for MockMvc. */
    public RequestPostProcessor bearer(TestUser user) {
        String token = accessToken(user);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /** A random client IP, so per-IP rate limits of one test do not affect another. */
    public static RequestPostProcessor randomIp() {
        String ip = "10.%d.%d.%d".formatted(ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(1, 255));
        return fromIp(ip);
    }

    public static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** A phone (local 9-digit form, as the frontend sends it) not used by any other test. */
    public static String uniqueLocalPhone() {
        return "9" + "%08d".formatted(SEQUENCE.incrementAndGet() % 100_000_000);
    }

    /** A unique email for users registered through the API. */
    public static String uniqueEmail() {
        return "api-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.ao";
    }
}
