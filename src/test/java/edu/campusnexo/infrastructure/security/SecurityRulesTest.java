package edu.campusnexo.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.infrastructure.security.Role.Access;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plain-Java rules of the security adapter: hashes, tokens, the access table and the rate limit. */
class SecurityRulesTest {
    /** Produced by tools/auth/hash_password.py: the Python tool and the backend agree on the format. */
    private static final String PYTHON_HASH = "pbkdf2-sha256$210000$zAlyDR8Ryyz0p3xu3FH5pQ==$17e/YdBcOp1CanTv2t70/fi6//aYGvA6hmx9D8QupHQ=";
    private static final Instant NOW = Instant.parse("2026-10-21T15:00:00Z");

    private static UserDirectory users() {
        return new UserDirectory("jurado:VIEWER:" + PYTHON_HASH + ",daniel:ADMIN:" + PYTHON_HASH,
                "clave-de-sensor-del-salon-421A", null);
    }

    @Test
    @DisplayName("Un hash del script de Python se verifica en Java; una contraseña distinta no")
    void pythonHashesVerify() {
        assertTrue(PasswordHasher.matches("clave-del-jurado".toCharArray(), PYTHON_HASH));
        assertFalse(PasswordHasher.matches("otra-clave".toCharArray(), PYTHON_HASH));
        String javaHash = PasswordHasher.hash("otra-clave".toCharArray());
        assertTrue(PasswordHasher.matches("otra-clave".toCharArray(), javaHash));
    }

    @Test
    @DisplayName("El token firmado vale hasta que expira, y no si se altera o el usuario deja de existir")
    void tokens() {
        UserDirectory directory = users();
        TokenService tokens = new TokenService("un-secreto-de-al-menos-32-caracteres!".getBytes(), Duration.ofHours(8),
                Clock.fixed(NOW, ZoneOffset.UTC), directory);
        Principal issued = tokens.issue(directory.find("jurado").orElseThrow());
        String token = tokens.encode(issued);
        assertEquals(Role.VIEWER, tokens.verify(token).orElseThrow().role());

        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertFalse(tokens.verify(tampered).isPresent());

        TokenService later = new TokenService("un-secreto-de-al-menos-32-caracteres!".getBytes(), Duration.ofHours(8),
                Clock.fixed(NOW.plus(Duration.ofHours(9)), ZoneOffset.UTC), directory);
        assertFalse(later.verify(token).isPresent());

        UserDirectory withoutJurado = new UserDirectory("daniel:ADMIN:" + PYTHON_HASH, null, null);
        TokenService revoked = new TokenService("un-secreto-de-al-menos-32-caracteres!".getBytes(), Duration.ofHours(8),
                Clock.fixed(NOW, ZoneOffset.UTC), withoutJurado);
        assertFalse(revoked.verify(token).isPresent());

        TokenService otherSecret = new TokenService("otro-secreto-de-al-menos-32-caracteres".getBytes(), Duration.ofHours(8),
                Clock.fixed(NOW, ZoneOffset.UTC), directory);
        assertFalse(otherSecret.verify(token).isPresent());
    }

    @Test
    @DisplayName("Tabla de acceso: lecturas para viewer, operaciones para operator, escenarios para admin, ingesta para sensores")
    void accessTable() {
        assertEquals(Access.PUBLIC, AccessPolicy.required("GET", "/actuator/health"));
        assertEquals(Access.PUBLIC, AccessPolicy.required("POST", "/api/v1/auth/login"));
        assertEquals(Access.VIEWER, AccessPolicy.required("GET", "/api/v1/twin/spaces"));
        assertEquals(Access.VIEWER, AccessPolicy.required("GET", "/api/v1/simulation/scenarios"));
        assertEquals(Access.ADMIN, AccessPolicy.required("POST", "/api/v1/simulation/REINICIAR"));
        assertEquals(Access.SENSOR, AccessPolicy.required("POST", "/api/v1/twin/environment"));
        assertEquals(Access.OPERATOR, AccessPolicy.required("POST", "/api/v1/spaces/421-A/reservations"));
        assertEquals(Access.OPERATOR, AccessPolicy.required("POST", "/api/v1/spaces/421-A/evaluations"));
        assertEquals(Access.OPERATOR, AccessPolicy.required("POST", "/api/v1/occupancy/estimate"));
        assertEquals(Access.OPERATOR, AccessPolicy.required("DELETE", "/api/v1/reservations/abc"));
        assertEquals(Access.ADMIN, AccessPolicy.required("PUT", "/api/v1/spaces/421-A"));
        assertEquals(Access.ADMIN, AccessPolicy.required("POST", "/api/v1/ruta-nueva"));

        assertTrue(Role.ADMIN.satisfies(Access.SENSOR));
        assertFalse(Role.SENSOR.satisfies(Access.VIEWER));
        assertFalse(Role.VIEWER.satisfies(Access.OPERATOR));
        assertTrue(Role.OPERATOR.satisfies(Access.VIEWER));
    }

    @Test
    @DisplayName("Las claves de sensor se comparan completas y deben ser largas; la configuración mal formada no arranca")
    void sensorKeysAndConfiguration() {
        UserDirectory directory = users();
        assertTrue(directory.isSensorKey("clave-de-sensor-del-salon-421A"));
        assertFalse(directory.isSensorKey("clave-de-sensor-del-salon-421"));
        assertThrows(IllegalStateException.class, () -> new UserDirectory(null, "corta", null));
        assertThrows(IllegalStateException.class, () -> new UserDirectory("x:ADMIN:" + PYTHON_HASH, null, null));
        assertThrows(IllegalStateException.class, () -> new UserDirectory("daniel:ROOT:" + PYTHON_HASH, null, null));
        assertThrows(IllegalStateException.class, () -> new UserDirectory("daniel:ADMIN:clave-en-claro", null, null));
    }

    @Test
    @DisplayName("El límite por ventana corta al pasar el máximo y se reinicia en la ventana siguiente")
    void rateLimit() {
        Instant[] now = {NOW};
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(1), clock);
        assertTrue(limiter.tryAcquire("ip"));
        assertTrue(limiter.tryAcquire("ip"));
        assertFalse(limiter.tryAcquire("ip"));
        assertTrue(limiter.tryAcquire("otra-ip"));
        now[0] = NOW.plus(Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("ip"));
    }
}
