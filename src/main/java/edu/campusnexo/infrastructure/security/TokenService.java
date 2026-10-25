package edu.campusnexo.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stateless session tokens: {@code base64url(v1|user|role|expiry).base64url(HMAC-SHA256)}. A token is
 * accepted only while it has not expired and its user still exists with the same role, so removing or
 * demoting someone in the configuration revokes their sessions.
 */
final class TokenService {
    private static final String VERSION = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;
    private final UserDirectory users;

    TokenService(byte[] secret, Duration ttl, Clock clock, UserDirectory users) {
        this.secret = secret.clone();
        this.ttl = ttl;
        this.clock = clock;
        this.users = users;
    }

    static byte[] randomSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    Principal issue(UserDirectory.Account account) {
        Instant expiresAt = clock.instant().plus(ttl);
        return new Principal(account.username(), account.role(), expiresAt);
    }

    String encode(Principal principal) {
        String payload = VERSION + "|" + principal.name() + "|" + principal.role() + "|" + principal.expiresAt().getEpochSecond();
        String body = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + ENCODER.encodeToString(sign(body));
    }

    Optional<Principal> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        String body = token.substring(0, dot);
        byte[] signature;
        String payload;
        try {
            signature = DECODER.decode(token.substring(dot + 1));
            payload = new String(DECODER.decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(sign(body), signature)) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 4 || !parts[0].equals(VERSION)) {
            return Optional.empty();
        }
        Instant expiresAt;
        Role role;
        try {
            expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[3]));
            role = Role.valueOf(parts[2]);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (!expiresAt.isAfter(clock.instant())) {
            return Optional.empty();
        }
        String name = parts[1];
        return users.find(name).filter(account -> account.role() == role)
                .map(account -> new Principal(name, role, expiresAt));
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 no está disponible en esta JVM", impossible);
        }
    }
}
