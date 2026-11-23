package com.minhaempresa.recrutamentoapi.shared.config;

import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Fills {@code createdAt}/{@code updatedAt} ({@code AuditableEntity}) and {@code createdBy}/
 * {@code updatedBy} ({@code UserAuditableEntity}).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** The authenticated user of the request; empty for system processes (seeds, bootstrap, jobs). */
    @Bean
    AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                return Optional.of(AuthenticatedUser.from(jwt).id());
            }
            return Optional.empty();
        };
    }
}
