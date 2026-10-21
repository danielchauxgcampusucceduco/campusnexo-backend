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
 * Starts the real application against PostgreSQL 17 (Flyway V1–V13, JPA validation) with security
 * enforced and the AI service unreachable, so the occupancy fallback decorator is exercised end to end.
 * Every call authenticates as the role it needs: a viewer reads, an operator books and evaluates, a
 * sensor pushes readings and only an administrator runs the defense scenarios.
 */
@SpringBootTest(classes = CampusNexoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CampusNexoApiIntegrationTest {
    private static final String SENSOR_KEY = "clave-de-sensor-para-las-pruebas";
    /** Password "clave-del-jurado", hashed with tools/auth/hash_password.py. */
    private static final String HASH = "pbkdf2-sha256$210000$zAlyDR8Ryyz0p3xu3FH5pQ==$17e/YdBcOp1CanTv2t70/fi6//aYGvA6hmx9D8QupHQ=";
    private static final String PASSWORD = "clave-del-jurado";
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
        registry.add("app.security.mode", () -> "enforced");
        registry.add("app.security.users", () -> "daniel:ADMIN:" + HASH + ",operador:OPERATOR:" + HASH + ",jurado:VIEWER:" + HASH);
        registry.add("app.security.sensor-keys", () -> SENSOR_KEY);
        registry.add("app.security.token-secret", () -> "secreto-de-pruebas-con-mas-de-32-caracteres");
        registry.add("app.security.writes-per-minute", () -> "10000");
        registry.add("app.security.login-attempts-per-minute", () -> "100");
        registry.add("app.simulation.enabled", () -> "true");
        registry.add("app.actuators.mode", () -> "log");
    }

    @Autowired
    private Environment environment;

    private RestClient client;
    /** Signed in once for the whole class (login is rate limited per client IP). */
    private static String admin;
    private static String operator;
    private static String viewer;

    /** How a request authenticates: nothing, a session token or a sensor key. */
    private record Credentials(String header, String value) {
        static final Credentials NONE = new Credentials(null, null);

        static Credentials bearer(String token) {
            return new Credentials("Authorization", "Bearer " + token);
        }
    }

    private static final Credentials SENSOR = new Credentials("X-API-Key", SENSOR_KEY);

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        if (admin == null) {
            admin = login("daniel");
            operator = login("operador");
            viewer = login("jurado");
        }
    }

    private String login(String username) {
        ResponseEntity<Map<String, Object>> response = client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("username", username, "password", PASSWORD))
                .retrieve().toEntity(OBJECT);
        assertEquals(200, response.getStatusCode().value());
        return (String) response.getBody().get("token");
    }

    private ResponseEntity<Map<String, Object>> post(String uri, Object body, Credentials credentials) {
        RestClient.RequestBodySpec spec = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        if (credentials.header() != null) {
            spec = spec.header(credentials.header(), credentials.value());
        }
        return spec.body(body).retrieve().toEntity(OBJECT);
    }

    private ResponseEntity<Map<String, Object>> post(String uri, Object body, String token) {
        return post(uri, body, Credentials.bearer(token));
    }

    private <T> ResponseEntity<T> get(String uri, String token, ParameterizedTypeReference<T> type) {
        return client.get().uri(uri).header("Authorization", "Bearer " + token).retrieve().toEntity(type);
    }

    private List<Map<String, Object>> list(String uri) {
        return get(uri, viewer, LIST).getBody();
    }

    private Map<String, Object> object(String uri) {
        return get(uri, viewer, OBJECT).getBody();
    }

    /** Alert observers run asynchronously: wait (bounded) until the expected alerts are stored. */
    private List<Map<String, Object>> awaitAlerts(String spaceId, int atLeast) throws InterruptedException {
        List<Map<String, Object>> alerts = List.of();
        for (int attempt = 0; attempt < 50 && alerts.size() < atLeast; attempt++) {
            alerts = list("/api/v1/alerts?spaceId=" + spaceId);
            if (alerts.size() < atLeast) {
                Thread.sleep(100);
            }
        }
        return alerts;
    }

    private static Map<String, Object> reading(String spaceId, Instant at, int people, double co2, double powerKw) {
        return Map.of("spaceId", spaceId, "recordedAt", at.toString(), "peopleCount", people, "objectCount", 0,
                "wifiDevices", people, "co2Ppm", co2, "temperatureC", 17.0, "humidityPct", 60.0,
                "illuminanceLux", 250.0, "activePowerKw", powerKw);
    }

    @Test
    @DisplayName("Catálogo sembrado por Flyway, con filtro por tipo")
    void catalog() {
        assertEquals(401, client.get().uri("/api/v1/spaces").retrieve().toEntity(OBJECT).getStatusCode().value());
        List<Map<String, Object>> spaces = list("/api/v1/spaces");
        assertNotNull(spaces);
        assertEquals(107, spaces.size());
        List<Map<String, Object>> libraries = list("/api/v1/spaces?type=BIBLIOTECA");
        assertNotNull(libraries);
        assertEquals(4, libraries.size());
        assertTrue(libraries.stream().anyMatch(space -> "BIBLIOTECA-2B".equals(space.get("spaceId"))));
    }

    @Test
    @DisplayName("Flujo completo: ingesta protegida → alertas → evaluación con IA caída → historial")
    void endToEndFlow() throws InterruptedException {
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(30);
        Map<String, Object> body = reading("421-A", at, 40, 1600, 2.5);

        assertEquals(401, post("/api/v1/telemetry", body, Credentials.NONE).getStatusCode().value());
        assertEquals(403, post("/api/v1/telemetry", body, viewer).getStatusCode().value());
        assertEquals(201, post("/api/v1/telemetry", body, SENSOR).getStatusCode().value());
        assertEquals(200, post("/api/v1/telemetry", body, SENSOR).getStatusCode().value());

        List<Map<String, Object>> alerts = awaitAlerts("421-A", 2);
        assertNotNull(alerts);
        assertTrue(alerts.stream().anyMatch(alert -> "CO2_ALTO".equals(alert.get("type"))));
        assertTrue(alerts.stream().anyMatch(alert -> "SOBRECUPO".equals(alert.get("type"))));

        assertEquals(403, post("/api/v1/spaces/421-A/evaluations", Map.of(), viewer).getStatusCode().value());
        ResponseEntity<Map<String, Object>> evaluation = post("/api/v1/spaces/421-A/evaluations", Map.of(), operator);
        assertEquals(200, evaluation.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> occupancy = (Map<String, Object>) evaluation.getBody().get("occupancy");
        assertEquals("RESPALDO_LOCAL", occupancy.get("source"));
        @SuppressWarnings("unchecked")
        Map<String, Object> recommendation = (Map<String, Object>) evaluation.getBody().get("recommendation");
        @SuppressWarnings("unchecked")
        Map<String, Object> hvac = (Map<String, Object>) recommendation.get("hvac");
        assertEquals("CALEFACCION", hvac.get("mode"));

        List<Map<String, Object>> history = list("/api/v1/spaces/421-A/climate-recommendations?limit=5");
        assertNotNull(history);
        assertFalse(history.isEmpty());

        Map<String, Object> summary = object("/api/v1/dashboard/summary");
        assertNotNull(summary);
        assertTrue(((Number) summary.get("alertsLast24h")).longValue() >= 2);
    }

    @Test
    @DisplayName("Ruta anterior del dashboard React sigue respondiendo con los mismos campos")
    void legacyOccupancyRoute() {
        ResponseEntity<Map<String, Object>> response = post("/api/v1/occupancy/estimate?spaceId=402-B",
                Map.of("co2Ppm", 820, "wifiDevices", 20), operator);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("occupancyEstimate"));
        assertNotNull(response.getBody().get("confidencePct"));
    }

    @Test
    @DisplayName("Reservas: creación con Location y conflicto por solapamiento")
    void reservations() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Map<String, Object> body = Map.of("startsAt", start.toString(), "endsAt", start.plus(2, ChronoUnit.HOURS).toString(),
                "expectedAttendees", 15, "reference", "IS-701");
        ResponseEntity<Map<String, Object>> created = post("/api/v1/spaces/424-A/reservations", body, operator);
        assertEquals(201, created.getStatusCode().value());
        assertNotNull(created.getHeaders().getLocation());
        ResponseEntity<Map<String, Object>> conflict = post("/api/v1/spaces/424-A/reservations", body, operator);
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("reservation_conflict", conflict.getBody().get("code"));
    }

    @Test
    @DisplayName("Errores con ProblemDetail y código estable")
    void errors() {
        ResponseEntity<Map<String, Object>> notFound = get("/api/v1/spaces/NO-EXISTE", viewer, OBJECT);
        assertEquals(404, notFound.getStatusCode().value());
        assertEquals("space_not_found", notFound.getBody().get("code"));

        ResponseEntity<Map<String, Object>> badLimit = get("/api/v1/telemetry?limit=500", viewer, OBJECT);
        assertEquals(400, badLimit.getStatusCode().value());
        assertEquals("invalid_request", badLimit.getBody().get("code"));

        ResponseEntity<Map<String, Object>> outOfRange = post("/api/v1/telemetry",
                reading("421-A", Instant.now().minusSeconds(5), 5, 20000, 1), SENSOR);
        assertEquals(400, outOfRange.getStatusCode().value());

        ResponseEntity<Map<String, Object>> missingField = post("/api/v1/telemetry", Map.of("spaceId", "421-A"), SENSOR);
        assertEquals(400, missingField.getStatusCode().value());
        assertEquals("validation_failed", missingField.getBody().get("code"));

        // 424-A only receives reservations in this suite, never telemetry.
        ResponseEntity<Map<String, Object>> noTelemetry = post("/api/v1/spaces/424-A/evaluations", Map.of(), operator);
        assertEquals(409, noTelemetry.getStatusCode().value());
    }

    @Test
    @DisplayName("Gemelo digital: frame edge por HTTP con el mismo JSON que MQTT")
    void edgeFrameOverHttp() {
        Map<String, Object> person = Map.of("track_id", 1, "class", "person", "confidence", 0.92,
                "bbox", List.of(0.10, 0.10, 0.15, 0.20));
        Map<String, Object> umbrella = Map.of("track_id", 2, "class", "umbrella", "confidence", 0.60,
                "bbox", List.of(0.30, 0.30, 0.35, 0.40));
        Map<String, Object> frame = Map.of("space_id", "316-B", "camera_online", true,
                "detections", List.of(person, umbrella),
                "sensors", Map.of("co2_ppm", 520, "temperature_c", 22.0, "humidity_pct", 55, "illuminance_lux", 300,
                        "wifi_devices", 1, "active_power_kw", 1.1));

        assertEquals(401, post("/api/v1/twin/telemetry", frame, Credentials.NONE).getStatusCode().value());
        assertEquals(403, post("/api/v1/twin/telemetry", frame, operator).getStatusCode().value());
        ResponseEntity<Map<String, Object>> response = post("/api/v1/twin/telemetry", frame, SENSOR);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("OCUPADO_OPTIMO", response.getBody().get("status"));
        assertNotNull(response.getBody().get("kalman"));
        assertNotNull(response.getBody().get("thermal"));
    }

    @Test
    @DisplayName("Escenarios de sustentación, gemelo, ahorro y trazas por REST")
    void simulationScenarios() {
        List<Map<String, Object>> scenarios = list("/api/v1/simulation/scenarios");
        assertNotNull(scenarios);
        assertEquals(13, scenarios.size());

        assertEquals(401, post("/api/v1/simulation/no_show", Map.of(), Credentials.NONE).getStatusCode().value());
        assertEquals(403, post("/api/v1/simulation/no_show", Map.of(), operator).getStatusCode().value());
        assertEquals(403, post("/api/v1/simulation/REINICIAR", Map.of(), viewer).getStatusCode().value());
        ResponseEntity<Map<String, Object>> noShow = post("/api/v1/simulation/no_show", Map.of(), admin);
        assertEquals(200, noShow.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> room = (Map<String, Object>) ((List<Map<String, Object>>) noShow.getBody().get("spaces")).get(0).get("state");
        assertEquals("LIBERADO_POR_NO_SHOW", room.get("status"));
        assertEquals("BLOQUEO_AHORRO_VACIO", room.get("hvacState"));

        ResponseEntity<Map<String, Object>> library = post("/api/v1/simulation/ACAPARAMIENTO_BIBLIOTECA", Map.of(), admin);
        @SuppressWarnings("unchecked")
        Map<String, Object> libraryState = (Map<String, Object>) ((List<Map<String, Object>>) library.getBody().get("spaces")).get(0).get("state");
        assertEquals(8, ((List<?>) libraryState.get("desks")).size());

        List<Map<String, Object>> twins = list("/api/v1/twin/spaces");
        assertNotNull(twins);
        assertEquals(107, twins.size());

        Map<String, Object> savings = object("/api/v1/twin/savings");
        assertNotNull(savings);
        assertNotNull(savings.get("totalKwh"));

        List<Map<String, Object>> traces = list("/api/v1/twin/traces?limit=5");
        assertNotNull(traces);
        assertFalse(traces.isEmpty());

        assertEquals(400, post("/api/v1/simulation/NO_EXISTE", Map.of(), admin).getStatusCode().value());
    }

    @Test
    @DisplayName("Lectura del Arduino por HTTP: luces encendidas en un salón vacío")
    void arduinoReadingOverHttp() {
        Map<String, Object> reading = Map.of("space_id", "512-A", "temperature_c", 19.2, "humidity_pct", 53,
                "illuminance_lux", 390, "lights_on", true, "devices_on", 0);
        assertEquals(401, post("/api/v1/twin/environment", reading, Credentials.NONE).getStatusCode().value());
        ResponseEntity<Map<String, Object>> response = post("/api/v1/twin/environment", reading, SENSOR);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("LUCES_SIN_OCUPACION", response.getBody().get("condition"));
        assertEquals(true, response.getBody().get("lightsOn"));
        assertEquals(19.2, ((Number) response.getBody().get("indoorTemperatureC")).doubleValue(), 1e-9);

        Map<String, Object> twin = object("/api/v1/twin/spaces/512-A");
        @SuppressWarnings("unchecked")
        Map<String, Object> space = (Map<String, Object>) twin.get("space");
        assertEquals(5, ((Number) space.get("floor")).intValue());
        assertEquals("BLOQUE-A", space.get("buildingId"));
        assertEquals(30, ((List<?>) twin.get("rois")).size());

        Map<String, Object> missing = Map.of("space_id", "512-A", "temperature_c", 19.2);
        assertEquals(400, post("/api/v1/twin/environment", missing, SENSOR).getStatusCode().value());
    }

    @Test
    @DisplayName("Sesión: credenciales malas → 401; la sesión pública dice quién es el token")
    void sessionAndLogin() {
        ResponseEntity<Map<String, Object>> wrong = client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("username", "jurado", "password", "incorrecta"))
                .retrieve().toEntity(OBJECT);
        assertEquals(401, wrong.getStatusCode().value());
        assertEquals("invalid_credentials", wrong.getBody().get("code"));

        Map<String, Object> anonymous = client.get().uri("/api/v1/auth/session").retrieve().body(OBJECT);
        assertEquals("enforced", anonymous.get("mode"));
        assertEquals(false, anonymous.get("authenticated"));
        Map<String, Object> session = get("/api/v1/auth/session", viewer, OBJECT).getBody();
        assertEquals("jurado", session.get("username"));
        assertEquals("VIEWER", session.get("role"));

        assertEquals(401, client.get().uri("/api/v1/twin/spaces").header("Authorization", "Bearer token-falso")
                .retrieve().toEntity(OBJECT).getStatusCode().value());
    }

    @Test
    @DisplayName("Dos reservas idénticas enviadas a la vez: PostgreSQL confirma una y la otra recibe 409")
    void concurrentReservationsCannotOverlap() throws Exception {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Map<String, Object> body = Map.of("startsAt", start.toString(), "endsAt", start.plus(1, ChronoUnit.HOURS).toString(),
                "expectedAttendees", 10, "reference", "IS-CONCURRENTE");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return post("/api/v1/spaces/423-A/reservations", body, operator).getStatusCode().value();
            }));
        }
        go.countDown();
        List<Integer> statuses = new java.util.ArrayList<>();
        for (java.util.concurrent.Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();
        assertEquals(1, statuses.stream().filter(status -> status == 201).count());
        assertEquals(5, statuses.stream().filter(status -> status == 409).count());
    }

    @Test
    @DisplayName("Paginación por cursor de la telemetría: X-Next-Cursor lleva a la página siguiente sin repetir")
    void telemetryPagination() {
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(600);
        for (int i = 0; i < 5; i++) {
            assertEquals(201, post("/api/v1/telemetry", reading("422-A", at.plusSeconds(i * 10L), 3, 600, 1), SENSOR)
                    .getStatusCode().value());
        }
        ResponseEntity<List<Map<String, Object>>> first = get("/api/v1/spaces/422-A/telemetry?limit=3", viewer, LIST);
        String cursor = first.getHeaders().getFirst("X-Next-Cursor");
        assertNotNull(cursor);
        ResponseEntity<List<Map<String, Object>>> second = get("/api/v1/spaces/422-A/telemetry?limit=3&cursor=" + cursor,
                viewer, LIST);
        assertEquals(3, first.getBody().size());
        assertTrue(second.getBody().size() >= 2);
        assertFalse(first.getBody().get(2).get("recordedAt").equals(second.getBody().get(0).get("recordedAt")));
        assertEquals(400, get("/api/v1/telemetry?cursor=no-es-un-cursor", viewer, OBJECT).getStatusCode().value());
    }
}
