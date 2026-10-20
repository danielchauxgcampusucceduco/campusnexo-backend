package edu.campusnexo.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.CampusNexoApplication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the real application against PostgreSQL 17 (Flyway V1–V9, JPA validation) with the AI
 * service unreachable, so the occupancy fallback decorator is exercised end to end.
 */
@SpringBootTest(classes = CampusNexoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CampusNexoApiIntegrationTest {
    private static final String API_KEY = "test-ingest-key";
    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withEnv("POSTGRES_DB", "campusnexo")
            .withEnv("POSTGRES_USER", "campusnexo")
            .withEnv("POSTGRES_PASSWORD", "campusnexo")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/campusnexo");
        registry.add("spring.datasource.username", () -> "campusnexo");
        registry.add("spring.datasource.password", () -> "campusnexo");
        registry.add("app.ai.base-url", () -> "http://127.0.0.1:9");
        registry.add("app.ai.connect-timeout", () -> "300ms");
        registry.add("app.ai.read-timeout", () -> "300ms");
        registry.add("app.security.ingest-api-key", () -> API_KEY);
    }

    @Autowired
    private Environment environment;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private ResponseEntity<Map<String, Object>> post(String uri, Object body, boolean withKey) {
        RestClient.RequestBodySpec spec = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        if (withKey) {
            spec = spec.header("X-API-Key", API_KEY);
        }
        return spec.body(body).retrieve().toEntity(OBJECT);
    }

    private static Map<String, Object> reading(String spaceId, Instant at, int people, double co2, double powerKw) {
        return Map.of("spaceId", spaceId, "recordedAt", at.toString(), "peopleCount", people, "objectCount", 0,
                "wifiDevices", people, "co2Ppm", co2, "temperatureC", 17.0, "humidityPct", 60.0,
                "illuminanceLux", 250.0, "activePowerKw", powerKw);
    }

    @Test
    @DisplayName("Catálogo sembrado por Flyway, con filtro por tipo")
    void catalog() {
        List<Map<String, Object>> spaces = client.get().uri("/api/v1/spaces").retrieve().body(LIST);
        assertNotNull(spaces);
        assertEquals(5, spaces.size());
        List<Map<String, Object>> libraries = client.get().uri("/api/v1/spaces?type=BIBLIOTECA").retrieve().body(LIST);
        assertNotNull(libraries);
        assertEquals("BIB-PISO-2", libraries.get(0).get("spaceId"));
    }

    @Test
    @DisplayName("Flujo completo: ingesta protegida → alertas → evaluación con IA caída → historial")
    void endToEndFlow() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(30);
        Map<String, Object> body = reading("AULA-301", at, 40, 1600, 2.5);

        assertEquals(401, post("/api/v1/telemetry", body, false).getStatusCode().value());
        assertEquals(201, post("/api/v1/telemetry", body, true).getStatusCode().value());
        assertEquals(200, post("/api/v1/telemetry", body, true).getStatusCode().value());

        List<Map<String, Object>> alerts = client.get().uri("/api/v1/alerts?spaceId=AULA-301").retrieve().body(LIST);
        assertNotNull(alerts);
        assertTrue(alerts.stream().anyMatch(alert -> "CO2_ALTO".equals(alert.get("type"))));
        assertTrue(alerts.stream().anyMatch(alert -> "SOBRECUPO".equals(alert.get("type"))));

        ResponseEntity<Map<String, Object>> evaluation = post("/api/v1/spaces/AULA-301/evaluations", Map.of(), false);
        assertEquals(200, evaluation.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> occupancy = (Map<String, Object>) evaluation.getBody().get("occupancy");
        assertEquals("RESPALDO_LOCAL", occupancy.get("source"));
        @SuppressWarnings("unchecked")
        Map<String, Object> recommendation = (Map<String, Object>) evaluation.getBody().get("recommendation");
        @SuppressWarnings("unchecked")
        Map<String, Object> hvac = (Map<String, Object>) recommendation.get("hvac");
        assertEquals("CALEFACCION", hvac.get("mode"));

        List<Map<String, Object>> history = client.get()
                .uri("/api/v1/spaces/AULA-301/climate-recommendations?limit=5").retrieve().body(LIST);
        assertNotNull(history);
        assertFalse(history.isEmpty());

        Map<String, Object> summary = client.get().uri("/api/v1/dashboard/summary").retrieve().body(OBJECT);
        assertNotNull(summary);
        assertTrue(((Number) summary.get("alertsLast24h")).longValue() >= 2);
    }

    @Test
    @DisplayName("Ruta anterior del dashboard React sigue respondiendo con los mismos campos")
    void legacyOccupancyRoute() {
        ResponseEntity<Map<String, Object>> response = post("/api/v1/occupancy/estimate?spaceId=LAB-IA-102",
                Map.of("co2Ppm", 820, "wifiDevices", 20), false);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("occupancyEstimate"));
        assertNotNull(response.getBody().get("confidencePct"));
    }

    @Test
    @DisplayName("Reservas: creación con Location y conflicto por solapamiento")
    void reservations() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Map<String, Object> body = Map.of("startsAt", start.toString(), "endsAt", start.plus(2, ChronoUnit.HOURS).toString(),
                "expectedAttendees", 20, "reference", "IS-701");
        ResponseEntity<Map<String, Object>> created = post("/api/v1/spaces/AULA-ADM-101/reservations", body, true);
        assertEquals(201, created.getStatusCode().value());
        assertNotNull(created.getHeaders().getLocation());
        ResponseEntity<Map<String, Object>> conflict = post("/api/v1/spaces/AULA-ADM-101/reservations", body, true);
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("reservation_conflict", conflict.getBody().get("code"));
    }

    @Test
    @DisplayName("Errores con ProblemDetail y código estable")
    void errors() {
        ResponseEntity<Map<String, Object>> notFound = client.get().uri("/api/v1/spaces/NO-EXISTE").retrieve().toEntity(OBJECT);
        assertEquals(404, notFound.getStatusCode().value());
        assertEquals("space_not_found", notFound.getBody().get("code"));

        ResponseEntity<Map<String, Object>> badLimit = client.get().uri("/api/v1/telemetry?limit=500").retrieve().toEntity(OBJECT);
        assertEquals(400, badLimit.getStatusCode().value());
        assertEquals("invalid_request", badLimit.getBody().get("code"));

        ResponseEntity<Map<String, Object>> outOfRange = post("/api/v1/telemetry",
                reading("AULA-301", Instant.now().minusSeconds(5), 5, 20000, 1), true);
        assertEquals(400, outOfRange.getStatusCode().value());

        ResponseEntity<Map<String, Object>> missingField = post("/api/v1/telemetry", Map.of("spaceId", "AULA-301"), true);
        assertEquals(400, missingField.getStatusCode().value());
        assertEquals("validation_failed", missingField.getBody().get("code"));

        ResponseEntity<Map<String, Object>> noTelemetry = post("/api/v1/spaces/BIB-PISO-2/evaluations", Map.of(), false);
        assertEquals(409, noTelemetry.getStatusCode().value());
    }
}
