package com.minhaempresa.recrutamentoapi.auth.controller;

import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties;
import com.minhaempresa.recrutamentoapi.auth.service.RefreshTokenService.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token travels only in an HttpOnly, Secure, SameSite=Strict cookie limited to
 * {@code /api/v1/auth}: JavaScript cannot read it and the browser only sends it to the auth endpoints.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenCookies {

    private final AuthProperties properties;
    private final Clock clock;

    ResponseCookie create(IssuedRefreshToken token) {
        Duration maxAge = Duration.between(clock.instant(), token.expiresAt());
        return base(token.value()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> properties.refreshCookie().name().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        AuthProperties.RefreshCookie cookie = properties.refreshCookie();
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite())
                .path(cookie.path());
    }
}
