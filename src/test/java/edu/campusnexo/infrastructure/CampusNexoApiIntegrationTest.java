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
        // The watchdog runs on real time; the tests drive the twin themselves.
        registry.add("app.twin.watchdog.enabled", () -> "false");
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/campusnexo");
        registry.add("spring.datasource.username", () -> "campusnexo");
        registry.add("spring.datasource.password", () -> "campusnexo");
        registry.add("app.ai.base-url", () -> "http://127.0.0.1:9");
        registry.add("app.ai.connect-timeout", () -> "300ms");
        registry.add("app.ai.read-timeout", () -> "300ms");
        registry.add("app.security.mode", () -> "enforced");
        registry.add("app.security.users", () -> "daniel:ADMIN:" + HASH + ",operador:OPERATOR:" + HASH
                + ",operadora:OPERATOR:" + HASH + ",jurado:VIEWER:" + HASH);
        registry.add("app.security.sensor-keys", () -> SENSOR_KEY);
        registry.add("app.security.token-secret", () -> "secreto-de-pruebas-con-mas-de-32-caracteres");
        registry.add("app.security.writes-per-minute", () -> "10000");
        registry.add("app.security.login-attempts-per-minute", () -> "100");
        // The administration answers only from the VPN; behind one proxy the caller is the last X-Forwarded-For entry.
        registry.add("app.security.admin-networks", () -> "127.0.0.1/32,::1/128");
        registry.add("app.security.trusted-proxies", () -> "1");
        // Pairing slots start at once and last one second, so the suite does not wait for real agents.
        registry.add("app.inventario.emparejamiento-anticipacion", () -> "0s");
        registry.add("app.inventario.emparejamiento-ranura", () -> "1s");
        registry.add("app.actuators.mode", () -> "log");
    }

    @Autowired
    private Environment environment;

    private RestClient client;
    /** Signed in once for the whole class (login is rate limited per client IP). */
    private static String admin;
    private static String operator;
    private static String otherOperator;
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
            otherOperator = login("operadora");
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
        assertEquals(109, spaces.size(), "107 salones del plano y los 2 salones piloto");
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

    private static Map<String, Object> person(int id, double x, double y) {
        return Map.of("track_id", id, "class", "person", "confidence", 0.9,
                "bbox", List.of(x - 0.02, y - 0.04, x + 0.02, y + 0.04));
    }

    private static Map<String, Object> frame(String spaceId, Instant at, List<Map<String, Object>> detections) {
        return Map.of("space_id", spaceId, "timestamp", at.toString(), "camera_online", true, "emulated", true,
                "detections", detections,
                "sensors", Map.of("co2_ppm", 650, "temperature_c", 18.0, "humidity_pct", 55, "illuminance_lux", 400,
                        "wifi_devices", detections.size(), "active_power_kw", 1.5));
    }

    private static boolean workstationsRegistered;

    /**
     * Registers the workstations of 301-A the way the pilot does: 30 agents report, the camera proposes the 30
     * seats (A4 without a chair), every computer shows its pattern in its slot and the administrator confirms.
     */
    private synchronized void ensureWorkstations() {
        if (workstationsRegistered) {
            return;
        }
        for (int n = 1; n <= 30; n++) {
            post("/api/v1/workstations/heartbeats", Map.of("cpu", String.format("PC-301A-%02d", n), "spaceId", "301-A"),
                    SENSOR);
        }
        List<Map<String, Object>> seats = new java.util.ArrayList<>();
        for (int row = 1; row <= 5; row++) {
            for (int column = 1; column <= 6; column++) {
                seats.add(Map.of("fila", row, "columna", column, "monitor", true,
                        "silla", !(row == 1 && column == 4), "mesa", true));
            }
        }
        assertEquals(202, post("/api/v1/workstations/discovery", Map.of("spaceId", "301-A", "emulado", true,
                "puestos", seats), SENSOR).getStatusCode().value());
        ResponseEntity<Map<String, Object>> pairing = post("/api/v1/admin/spaces/301-A/emparejamiento", Map.of(), admin);
        assertEquals(200, pairing.getStatusCode().value());
        Instant start = Instant.parse((String) pairing.getBody().get("inicio"));
        @SuppressWarnings("unchecked")
        List<String> cpus = (List<String>) pairing.getBody().get("cpus");
        for (int i = 0; i < cpus.size(); i++) {
            int seat = Integer.parseInt(cpus.get(i).substring("PC-301A-".length()));
            assertEquals(202, post("/api/v1/workstations/pairing", Map.of("spaceId", "301-A",
                    "fila", (seat - 1) / 6 + 1, "columna", (seat - 1) % 6 + 1,
                    "at", start.plusMillis(i * 1_000L + 500).toString()), SENSOR).getStatusCode().value());
        }
        ResponseEntity<List<Map<String, Object>>> confirmed = client.post().uri("/api/v1/admin/spaces/301-A/propuesta/confirmar")
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + admin)
                .body(Map.of()).retrieve().toEntity(LIST);
        assertEquals(200, confirmed.getStatusCode().value());
        assertEquals(30, confirmed.getBody().size());
        workstationsRegistered = true;
    }

    @Test
    @DisplayName("Registro automático: la cámara propone los 30 puestos de 301-A y cada PC se empareja por su patrón")
    void cameraRegistersTheWorkstations() {
        ensureWorkstations();
        List<Map<String, Object>> puestos = get("/api/v1/admin/spaces/301-A/puestos", admin, LIST).getBody();
        assertEquals(30, puestos.size());
        assertEquals(List.of("SILLA"), puestos.get(3).get("faltantes"), "A4 se registró sin silla, para revisar");
        assertTrue(puestos.stream().allMatch(p -> ((List<?>) p.get("activos")).stream()
                .anyMatch(a -> "CPU".equals(((Map<?, ?>) a).get("tipo")))), "cada PC quedó en su puesto");
    }

    @Test
    @DisplayName("Profesor frente al tablero y estudiantes al fondo de 303-A: roles por zona y clase reconocida")
    void teacherInFrontAndStudentsAtTheBack() {
        List<Map<String, Object>> detections = new java.util.ArrayList<>();
        detections.add(person(1, 0.50, 0.15));
        for (int column = 0; column < 6; column++) {
            detections.add(person(10 + column, 0.05 + 0.15 * (column + 0.5), 0.30 + 0.65 / 6 * 5.5));
        }
        ResponseEntity<Map<String, Object>> response = post("/api/v1/twin/telemetry",
                frame("303-A", Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(10), detections), SENSOR);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, ((Number) response.getBody().get("teachersPresent")).intValue());
        assertEquals(6, ((Number) response.getBody().get("studentsPresent")).intValue());
        assertEquals(true, response.getBody().get("emulated"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> escenarios = (List<Map<String, Object>>) response.getBody().get("escenarios");
        assertTrue(escenarios.stream().anyMatch(e -> "CLASE_EN_CURSO".equals(e.get("escenario"))));

        assertEquals(109, list("/api/v1/twin/spaces").size());
        assertNotNull(object("/api/v1/twin/savings").get("totalKwh"));
        assertFalse(list("/api/v1/twin/traces?limit=5").isEmpty());
    }

    private ResponseEntity<Map<String, Object>> fromAddress(String forwardedFor, String uri, Object body) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin).header("X-Forwarded-For", forwardedFor)
                .body(body).retrieve().toEntity(OBJECT);
    }

    @Test
    @DisplayName("Administración: las medidas tomadas en sitio ajustan el salón sin dejar puestos fuera de la rejilla")
    void administrationAdjustsARoom() {
        Map<String, Object> medidas = new java.util.HashMap<>(Map.of("capacity", 38, "areaSqm", 56.5, "seatRows", 6,
                "seatColumns", 6, "computers", 0, "hvacInstalled", true));
        ResponseEntity<Map<String, Object>> adjusted = client.put().uri("/api/v1/admin/spaces/303-A")
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).body(medidas)
                .retrieve().toEntity(OBJECT);
        assertEquals(200, adjusted.getStatusCode().value());
        assertEquals(38, ((Number) object("/api/v1/spaces/303-A").get("capacity")).intValue(),
                "el proxy del catálogo descarta su copia al escribir");

        medidas.put("seatRows", 5);
        ResponseEntity<Map<String, Object>> shrunk = client.put().uri("/api/v1/admin/spaces/303-A")
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).body(medidas)
                .retrieve().toEntity(OBJECT);
        assertEquals(409, shrunk.getStatusCode().value());
        assertEquals("puestos_fuera_de_rejilla", shrunk.getBody().get("code"));
    }

    @Test
    @DisplayName("Administración: solo ADMIN y solo desde la red de la VPN; un X-Forwarded-For falso no la abre")
    void administrationNeedsTheRoleAndTheNetwork() {
        Map<String, Object> docente = Map.of("tipo", "DOCENTE", "codigo", "D-100", "nombre", "Marta Ruiz",
                "adscripcion", "Ingeniería de Software");
        assertEquals(403, post("/api/v1/admin/personas", docente, operator).getStatusCode().value());
        assertEquals(403, get("/api/v1/admin/personas", viewer, OBJECT).getStatusCode().value(),
                "los datos de personas no se leen sin ser administrador");
        ResponseEntity<Map<String, Object>> outside = fromAddress("198.51.100.9", "/api/v1/admin/personas", docente);
        assertEquals(403, outside.getStatusCode().value());
        assertEquals("admin_network_required", outside.getBody().get("code"));
        assertEquals(403, fromAddress("127.0.0.1, 198.51.100.9", "/api/v1/admin/personas", docente)
                .getStatusCode().value());

        ResponseEntity<Map<String, Object>> created = post("/api/v1/admin/personas", docente, admin);
        assertEquals(200, created.getStatusCode().value());
        assertEquals(4, ((Number) created.getBody().get("reservaMaximaHoras")).intValue());
        assertEquals(36, get("/api/v1/admin/spaces/303-A/puestos", admin, LIST).getBody().size());
    }

    @Test
    @DisplayName("Reserva con titular y check-in en el kiosco con su código institucional")
    void holderChecksInAtTheKiosk() {
        post("/api/v1/admin/personas", Map.of("tipo", "DOCENTE", "codigo", "D-200", "nombre", "Luis Paz",
                "adscripcion", "Matemáticas"), admin);
        Instant start = Instant.now().minus(5, ChronoUnit.MINUTES);
        Map<String, Object> booking = Map.of("startsAt", start.toString(),
                "endsAt", start.plus(2, ChronoUnit.HOURS).toString(),
                "expectedAttendees", 20, "reference", "MAT-101", "holderCode", "D-200");
        ResponseEntity<Map<String, Object>> created = post("/api/v1/spaces/301-A/reservations", booking, operator);
        assertEquals(201, created.getStatusCode().value());
        assertEquals("D-200", created.getBody().get("holderCode"));

        assertEquals(403, post("/api/v1/spaces/301-A/check-in", Map.of("codigo", "D-200"), operator)
                .getStatusCode().value());
        assertEquals(400, post("/api/v1/spaces/301-A/check-in", Map.of("codigo", "D-999"), SENSOR)
                .getStatusCode().value());
        ResponseEntity<Map<String, Object>> checkIn = post("/api/v1/spaces/301-A/check-in", Map.of("codigo", "D-200"),
                SENSOR);
        assertEquals(200, checkIn.getStatusCode().value());
        assertEquals("Luis Paz", checkIn.getBody().get("nombre"));
        assertNotNull(checkIn.getBody().get("checkedInAt"));
    }

    @Test
    @DisplayName("Computador encendido con 301-A vacío: alerta; la auditoría del borde señala la silla que no se ve")
    void computerLeftOnAndMissingChair() throws InterruptedException {
        ensureWorkstations();
        assertEquals(200, post("/api/v1/twin/telemetry",
                frame("301-A", Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5), List.of()), SENSOR)
                .getStatusCode().value());

        ResponseEntity<Map<String, Object>> heartbeat = post("/api/v1/workstations/heartbeats",
                Map.of("cpu", "PC-301A-05"), SENSOR);
        assertEquals(202, heartbeat.getStatusCode().value());
        assertEquals(true, heartbeat.getBody().get("asignado"));
        assertEquals(true, ((Map<?, ?>) heartbeat.getBody().get("puesto")).get("cpuSinUsuario"));
        assertEquals(404, post("/api/v1/workstations/heartbeats", Map.of("cpu", "NO-EXISTE"), SENSOR)
                .getStatusCode().value());
        assertTrue(awaitAlerts("301-A", 1).stream().anyMatch(a -> "CPU_ENCENDIDA_SIN_USUARIO".equals(a.get("type"))));

        ResponseEntity<List<Map<String, Object>>> audit = client.post().uri("/api/v1/workstations/audit")
                .contentType(MediaType.APPLICATION_JSON).header("X-API-Key", SENSOR_KEY)
                .body(Map.of("spaceId", "301-A", "puestos", List.of(Map.of("zoneId", "301-A-P07", "silla", false,
                        "mesa", true))))
                .retrieve().toEntity(LIST);
        assertEquals(200, audit.getStatusCode().value());
        assertEquals("301A-SILLA-07", audit.getBody().getFirst().get("codigoActivo"));
    }

    @Test
    @DisplayName("Sin historial configurado el pronóstico responde 409 con un código estable")
    void forecastWithoutHistory() {
        ResponseEntity<Map<String, Object>> forecast = get("/api/v1/admin/spaces/301-A/pronostico", admin, OBJECT);
        assertEquals(409, forecast.getStatusCode().value());
        assertEquals("history_not_available", forecast.getBody().get("code"));
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
        assertEquals(36, ((List<?>) twin.get("rois")).size());

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

    @Test
    @DisplayName("El canal en vivo (STOMP) exige sesión: sin token se rechaza, con token de viewer conecta")
    void liveFeedRequiresSignIn() throws Exception {
        String url = "ws://localhost:" + environment.getProperty("local.server.port") + "/ws";
        org.springframework.web.socket.messaging.WebSocketStompClient stomp =
                new org.springframework.web.socket.messaging.WebSocketStompClient(
                        new org.springframework.web.socket.client.standard.StandardWebSocketClient());
        org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter handler =
                new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() { };

        org.springframework.messaging.simp.stomp.StompHeaders anonymous = new org.springframework.messaging.simp.stomp.StompHeaders();
        boolean rejected;
        try {
            stomp.connectAsync(url, new org.springframework.web.socket.WebSocketHttpHeaders(), anonymous, handler)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            rejected = false;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException expected) {
            rejected = true;
        }
        assertTrue(rejected);

        org.springframework.messaging.simp.stomp.StompHeaders signedIn = new org.springframework.messaging.simp.stomp.StompHeaders();
        signedIn.add("Authorization", "Bearer " + viewer);
        org.springframework.messaging.simp.stomp.StompSession session = stomp.connectAsync(url,
                new org.springframework.web.socket.WebSocketHttpHeaders(), signedIn, handler)
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(session.isConnected());
        session.disconnect();
        stomp.stop();
    }

    private ResponseEntity<Map<String, Object>> delete(String uri, String token) {
        return client.delete().uri(uri).header("Authorization", "Bearer " + token).retrieve().toEntity(OBJECT);
    }

    @Test
    @DisplayName("Una reserva solo la cancela quien la creó o un administrador; el autor queda registrado")
    void reservationsAreCancelledOnlyByTheirAuthor() {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Map<String, Object> body = Map.of("startsAt", start.toString(), "endsAt", start.plus(1, ChronoUnit.HOURS).toString(),
                "expectedAttendees", 12, "reference", "IS-801");
        ResponseEntity<Map<String, Object>> created = post("/api/v1/spaces/424-A/reservations", body, operator);
        assertEquals(201, created.getStatusCode().value());
        assertEquals("operador", created.getBody().get("createdBy"));
        String uri = "/api/v1/reservations/" + created.getBody().get("reservationId");

        ResponseEntity<Map<String, Object>> denied = delete(uri, otherOperator);
        assertEquals(403, denied.getStatusCode().value());
        assertEquals("reservation_not_owned", denied.getBody().get("code"));
        assertEquals(204, delete(uri, admin).getStatusCode().value());
    }

    @Test
    @DisplayName("Un frame repetido por HTTP no cambia el gemelo ni duplica la lectura, y queda contado en Prometheus")
    void redeliveredEdgeFrameIsIgnoredAndCounted() {
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(20);
        Map<String, Object> frame = Map.of("space_id", "318-B", "timestamp", capturedAt.toString(), "camera_online", true,
                "detections", List.of(Map.of("track_id", 1, "class", "person", "confidence", 0.9,
                        "bbox", List.of(0.10, 0.10, 0.15, 0.20))),
                "sensors", Map.of("co2_ppm", 520, "temperature_c", 21.5, "humidity_pct", 55, "illuminance_lux", 300,
                        "wifi_devices", 1, "active_power_kw", 1.0));

        ResponseEntity<Map<String, Object>> first = post("/api/v1/twin/telemetry", frame, SENSOR);
        ResponseEntity<Map<String, Object>> again = post("/api/v1/twin/telemetry", frame, SENSOR);

        assertEquals(200, first.getStatusCode().value());
        assertEquals(200, again.getStatusCode().value());
        assertEquals(first.getBody().get("updatedAt"), again.getBody().get("updatedAt"));
        assertNotNull(first.getBody().get("hvacMode"));
        List<Map<String, Object>> readings = list("/api/v1/spaces/318-B/telemetry?limit=10");
        assertEquals(1, readings.size());
        assertEquals(1, ((Number) readings.get(0).get("cameraPeople")).intValue());

        assertEquals(401, client.get().uri("/actuator/prometheus").retrieve().toEntity(String.class)
                .getStatusCode().value());
        assertEquals(403, client.get().uri("/actuator/prometheus").header("Authorization", "Bearer " + viewer)
                .retrieve().toEntity(String.class).getStatusCode().value());
        ResponseEntity<String> metrics = client.get().uri("/actuator/prometheus")
                .header("Authorization", "Bearer " + admin).retrieve().toEntity(String.class);
        assertEquals(200, metrics.getStatusCode().value());
        String discarded = metrics.getBody().lines().filter(line -> line.startsWith("campusnexo_readings_discarded_total"))
                .filter(line -> line.contains("reason=\"duplicada\"") && line.contains("source=\"camara\""))
                .findFirst().orElse("");
        assertFalse(discarded.isEmpty(), metrics.getBody());
    }
}
