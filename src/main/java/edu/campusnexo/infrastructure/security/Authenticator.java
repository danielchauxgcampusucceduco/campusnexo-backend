package edu.campusnexo.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns request credentials into a {@link Principal}: {@code Authorization: Bearer <token>} for people,
 * {@code X-API-Key} for sensors. In development mode a request without credentials acts as the local
 * administrator; in enforced mode it stays anonymous.
 */
public final class Authenticator {
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final Logger log = LoggerFactory.getLogger(Authenticator.class);
    private static final String BEARER = "Bearer ";

    private final UserDirectory users;
    private final TokenService tokens;
    private final boolean development;
    private final Clock clock;

    Authenticator(SecurityProperties properties, Clock clock) {
        this.development = properties.development();
        this.clock = clock;
        this.users = new UserDirectory(properties.users(), properties.sensorKeys(), properties.ingestApiKey());
        byte[] secret;
        String configured = properties.tokenSecret();
        if (configured != null && !configured.isBlank()) {
            if (configured.length() < 32) {
                throw new IllegalStateException("APP_TOKEN_SECRET debe tener al menos 32 caracteres");
            }
            secret = PasswordHasher.utf8(configured);
        } else if (development) {
            secret = TokenService.randomSecret();
        } else {
            throw new IllegalStateException("APP_TOKEN_SECRET es obligatorio con app.security.mode=enforced");
        }
        if (!development && !users.hasAdmin()) {
            throw new IllegalStateException("Con app.security.mode=enforced, APP_USERS debe tener al menos un ADMIN");
        }
        this.tokens = new TokenService(secret, properties.tokenTtl(), clock, users);
        if (development) {
            log.warn("SEGURIDAD EN MODO DESARROLLO: las solicitudes sin credenciales actúan como administrador. "
                    + "No uses este modo en un servidor accesible desde la red.");
        } else {
            log.info("Seguridad activa: {} usuarios, {} claves de sensor", users.userCount(), users.sensorKeyCount());
        }
    }

    public boolean development() {
        return development;
    }

    Optional<Principal> resolve(String authorization, String apiKey) {
        if (authorization != null && authorization.startsWith(BEARER)) {
            return tokens.verify(authorization.substring(BEARER.length()).trim());
        }
        if (apiKey != null) {
            int key = users.sensorKeyIndex(apiKey);
            return key >= 0 ? Optional.of(new Principal("sensor-" + (key + 1), Role.SENSOR, null)) : Optional.empty();
        }
        return development ? Optional.of(new Principal("desarrollo-local", Role.ADMIN, null)) : Optional.empty();
    }

    /** Verifies a password and issues a session; empty when the name or password is wrong. */
    Optional<Session> login(String username, char[] password) {
        return users.authenticate(username, password).map(account -> {
            Principal principal = tokens.issue(account);
            return new Session(tokens.encode(principal), principal);
        });
    }

    Instant now() {
        return clock.instant();
    }

    record Session(String token, Principal principal) {
    }
}
