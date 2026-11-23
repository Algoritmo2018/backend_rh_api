package com.minhaempresa.recrutamentoapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.minhaempresa.recrutamentoapi.auth.service.AuthProperties;
import com.minhaempresa.recrutamentoapi.auth.service.RateLimiter;
import com.minhaempresa.recrutamentoapi.shared.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static RateLimiter limiter(long maxEntries) {
        AuthProperties.Limit any = new AuthProperties.Limit(10, Duration.ofMinutes(1));
        AuthProperties properties = new AuthProperties(
                "x".repeat(32), false, Set.of(), Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5, Duration.ofMinutes(5), 5,
                Duration.ofHours(1), Duration.ofDays(7), Duration.ofHours(48), 5, Duration.ofMinutes(15),
                new AuthProperties.RefreshCookie("refresh_token", "/api/v1/auth", true, "Strict"),
                new AuthProperties.RateLimits(maxEntries, any, any, any, any, any, any, any, any));
        return new RateLimiter(properties);
    }

    @Test
    void allowsUpToTheLimitPerKeyAndWindow() {
        RateLimiter limiter = limiter(1_000);
        for (int i = 0; i < 3; i++) {
            limiter.check("rule", "10.0.0.1", 3, Duration.ofMinutes(1));
        }

        assertThatThrownBy(() -> limiter.check("rule", "10.0.0.1", 3, Duration.ofMinutes(1)))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1)));
        assertThatCode(() -> limiter.check("rule", "10.0.0.2", 3, Duration.ofMinutes(1))).as("other key").doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("other-rule", "10.0.0.1", 3, Duration.ofMinutes(1))).as("other rule").doesNotThrowAnyException();
    }

    @Test
    void memoryIsBoundedByMaxEntries() {
        RateLimiter limiter = limiter(100);

        for (int i = 0; i < 5_000; i++) {
            limiter.check("flood", "10.1." + (i / 256) + "." + (i % 256), 5, Duration.ofHours(1));
        }

        await().atMost(Duration.ofSeconds(5)).until(() -> limiter.trackedKeys("flood") <= 100);
    }

    @Test
    void expiredWindowsAreRemovedAndTheKeyCanTryAgain() {
        RateLimiter limiter = limiter(1_000);
        Duration window = Duration.ofMillis(200);
        limiter.check("short", "10.0.0.9", 1, window);
        assertThatThrownBy(() -> limiter.check("short", "10.0.0.9", 1, window)).isInstanceOf(TooManyRequestsException.class);

        await().atMost(Duration.ofSeconds(5)).until(() -> limiter.trackedKeys("short") == 0);
        assertThatCode(() -> limiter.check("short", "10.0.0.9", 1, window)).doesNotThrowAnyException();
    }
}
