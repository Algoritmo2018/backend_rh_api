package com.minhaempresa.recrutamentoapi.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.minhaempresa.recrutamentoapi.shared.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter kept in memory (per application instance).
 *
 * <p>One Caffeine cache per rule: an entry is created on the first hit of a key and expires one window
 * later, so expired entries are cleaned up automatically, and each cache holds at most
 * {@code app.auth.rate-limits.max-entries} keys (the least used are evicted), so memory stays bounded.
 *
 * <p>With several instances each one counts separately; move to the database or Redis if needed.
 */
@Component
public class RateLimiter {

    private final long maxEntries;
    private final Map<String, Cache<String, Window>> caches = new ConcurrentHashMap<>();

    public RateLimiter(AuthProperties properties) {
        this.maxEntries = properties.rateLimits().maxEntries();
    }

    /**
     * Counts one hit of {@code key} for the rule.
     *
     * @throws TooManyRequestsException when the limit of the current window is exceeded
     */
    public void check(String rule, String key, int max, Duration window) {
        Cache<String, Window> cache = caches.computeIfAbsent(rule, r -> Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(window)
                .build());
        Window current = cache.get(key, k -> new Window(System.nanoTime()));
        if (current.hits.incrementAndGet() > max) {
            long elapsed = System.nanoTime() - current.startedAt;
            throw new TooManyRequestsException(window.minusNanos(elapsed));
        }
    }

    public void check(String rule, String key, AuthProperties.Limit limit) {
        check(rule, key, limit.max(), limit.window());
    }

    /** Number of keys currently tracked for a rule (for tests and monitoring). */
    public long trackedKeys(String rule) {
        Cache<String, Window> cache = caches.get(rule);
        if (cache == null) {
            return 0;
        }
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger hits = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
