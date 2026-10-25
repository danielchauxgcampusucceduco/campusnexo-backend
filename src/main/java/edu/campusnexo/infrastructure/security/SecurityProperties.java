package edu.campusnexo.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.security.*}.
 *
 * <ul>
 *   <li>{@code mode}: {@code enforced} (default, also forced by the {@code prod} profile) or
 *       {@code development}, where requests without credentials act as a local administrator. Fails
 *       closed: an unknown value is rejected at startup.</li>
 *   <li>{@code users}: {@code name:ROLE:hash} entries separated by commas; hashes are
 *       {@code pbkdf2-sha256$iterations$salt$hash} (see {@code tools/auth/hash_password.py}).</li>
 *   <li>{@code sensor-keys}: keys for cameras and boards ({@code X-API-Key}); {@code ingest-api-key} is the
 *       former single key and is accepted as one more sensor key.</li>
 *   <li>{@code token-secret}: HMAC key of the session tokens, at least 32 characters when enforced.</li>
 *   <li>{@code trusted-proxies}: proxies in front of the backend that append to {@code X-Forwarded-For}
 *       (Render: 1); 0 uses the connection's address.</li>
 *   <li>{@code admin-networks}: CIDR networks, separated by commas, from which {@code /api/v1/admin/**} answers
 *       (the VPN's public address); empty accepts any address, and production refuses to start without it.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String mode, String users, String sensorKeys, String ingestApiKey, String tokenSecret,
                                 Duration tokenTtl, int loginAttemptsPerMinute, int writesPerMinute,
                                 int trustedProxies, String adminNetworks) {

    public boolean development() {
        return switch (mode == null ? "enforced" : mode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "enforced" -> false;
            case "development" -> true;
            default -> throw new IllegalStateException("app.security.mode debe ser 'enforced' o 'development': " + mode);
        };
    }
}
