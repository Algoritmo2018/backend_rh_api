package com.minhaempresa.recrutamentoapi.shared.security;

import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API secured with Bearer JWTs. Public endpoints live under {@code /api/v1/public/**} and
 * {@code /api/v1/auth/**}; everything else requires a valid token. Role checks are done with
 * {@code @PreAuthorize} in the modules (method security is enabled).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    private static final String AUTH = "/api/v1/auth";

    /** Every auth endpoint is public except {@code /auth/me} and {@code /auth/email/verify/resend}. */
    private static final String[] PUBLIC_PATHS = {
        "/api/v1/public/**",
        AUTH + "/login",
        AUTH + "/login/two-factor",
        AUTH + "/otp/request",
        AUTH + "/otp/verify",
        AUTH + "/register",
        AUTH + "/refresh",
        AUTH + "/logout",
        AUTH + "/password/forgot",
        AUTH + "/password/reset",
        AUTH + "/invitations/accept",
        AUTH + "/email/verify",
        AUTH + "/email/change/confirm",
        "/actuator/health",
        "/actuator/health/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/error",
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /**
     * Credentials (the refresh-token cookie) are only allowed on the auth endpoints; the rest of the
     * API uses Bearer tokens only. The first matching pattern wins, so the auth one comes first.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(AUTH + "/**", cors(properties, true));
        source.registerCorsConfiguration("/api/**", cors(properties, false));
        return source;
    }

    private static CorsConfiguration cors(AppProperties properties, boolean allowCredentials) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // File downloads (CV, exports) need the file name; 429 responses carry Retry-After
        cors.setExposedHeaders(List.of("Content-Disposition", "Retry-After"));
        cors.setAllowCredentials(allowCredentials);
        cors.setMaxAge(Duration.ofHours(1));
        return cors;
    }
}
