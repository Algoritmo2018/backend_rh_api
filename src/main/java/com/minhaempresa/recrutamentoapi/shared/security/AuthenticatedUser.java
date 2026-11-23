package com.minhaempresa.recrutamentoapi.shared.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The user of the current request, read from the access token. Declare it as a controller parameter
 * and pass it to the service: modules must use it instead of ids sent by the client.
 *
 * <p>Note: the token is valid until it expires (15 min), so a deactivation or role change only takes
 * effect on the next refresh.
 *
 * @param id user id ({@code sub})
 * @param role role as the Java constant name ({@code ADMIN}, {@code RECRUITER}…)
 * @param sessionId refresh-token family of this login ({@code sid})
 * @param clientId client company (only for {@code CLIENT})
 * @param candidateId candidate profile (only for {@code CANDIDATE})
 */
public record AuthenticatedUser(UUID id, String role, UUID sessionId, UUID clientId, UUID candidateId) {

    public static AuthenticatedUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtClaims.ROLES);
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                roles == null || roles.isEmpty() ? null : roles.getFirst(),
                uuidClaim(jwt, JwtClaims.SESSION_ID),
                uuidClaim(jwt, JwtClaims.CLIENT_ID),
                uuidClaim(jwt, JwtClaims.CANDIDATE_ID));
    }

    public boolean hasRole(String roleName) {
        return roleName.equals(role);
    }

    private static UUID uuidClaim(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        return value == null ? null : UUID.fromString(value);
    }
}
