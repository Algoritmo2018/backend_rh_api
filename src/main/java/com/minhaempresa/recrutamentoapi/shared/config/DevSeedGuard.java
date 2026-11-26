package com.minhaempresa.recrutamentoapi.shared.config;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;

/**
 * Refuses to load the development seed ({@code dev/seed}: accounts that share a known password) anywhere but a
 * local machine. Checked before Flyway migrates (see {@link DevSeedFlywayConfiguration}).
 *
 * <ul>
 *   <li>Never together with the {@code prod} profile.</li>
 *   <li>Only when the host of {@code app.frontend-url} is {@code localhost} or {@code 127.0.0.1}, unless
 *       {@code app.devseed.allow-remote} ({@code APP_DEVSEED_ALLOW_REMOTE=true}) is set explicitly.</li>
 * </ul>
 */
public final class DevSeedGuard {

    public static final String DEVSEED_PROFILE = "devseed";
    public static final String ALLOW_REMOTE_PROPERTY = "app.devseed.allow-remote";
    private static final String SEED_LOCATION = "dev/seed";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

    private DevSeedGuard() {}

    /**
     * @param flywayLocations the Flyway locations about to be migrated (e.g. {@code filesystem:dev/seed})
     * @throws IllegalStateException when the seed would be loaded where it must not
     */
    public static void check(Environment environment, Collection<String> flywayLocations) {
        if (!seedRequested(environment, flywayLocations)) {
            return;
        }
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("Refusing to start: the development seed (dev/seed) must never be loaded with the "
                    + "'prod' profile. Its accounts share a known password (written in dev/README.md), so anyone could log in as an "
                    + "administrator. Remove the 'devseed' profile.");
        }
        String frontendUrl = environment.getProperty("app.frontend-url", "");
        boolean allowRemote = environment.getProperty(ALLOW_REMOTE_PROPERTY, Boolean.class, false);
        if (!isLocal(frontendUrl) && !allowRemote) {
            throw new IllegalStateException("Refusing to start: the development seed (dev/seed) is only for a local machine, "
                    + "but app.frontend-url is '" + frontendUrl + "' (not localhost or 127.0.0.1). The seeded accounts share a known "
                    + "password (written in dev/README.md), so anyone who reaches this server could log in as an administrator. "
                    + "Remove the 'devseed' profile, or set APP_DEVSEED_ALLOW_REMOTE=true only for a closed test server.");
        }
    }

    static boolean seedRequested(Environment environment, Collection<String> flywayLocations) {
        return environment.matchesProfiles(DEVSEED_PROFILE)
                || flywayLocations.stream().anyMatch(l -> l.replace('\\', '/').contains(SEED_LOCATION));
    }

    static boolean isLocal(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
