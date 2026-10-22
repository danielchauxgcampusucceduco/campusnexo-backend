package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.application.port.in.CreateReservationCommand;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.trace.TraceStep;
import edu.campusnexo.domain.twin.EnvironmentReading;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.RoomCondition;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.support.TwinTestFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TwinPipelineTest {
    private final TwinTestFixture fixture = new TwinTestFixture();

    private TelemetrySnapshot frame(int people, double co2, double indoorC) {
        List<TrackedDetection> detections = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            detections.add(new TrackedDetection(i, DetectedObjectClass.PERSON, 0.9, new BoundingBox(0.1, 0.1, 0.14, 0.17)));
        }
        return new TelemetrySnapshot("LAB-IA-102", fixture.clock.instant(), true, detections, co2, indoorC, 14.0, 55, 300,
                people, 2.0);
    }

    @Test
    @DisplayName("Un frame actualiza el gemelo, guarda la lectura agregada, comanda el HVAC y publica la traza")
    void processesFrameEndToEnd() {
        SpaceTwinState state = fixture.pipeline.process(frame(31, 900, 23.5), "MQTT · prueba");

        assertEquals(SpaceStatus.ALERTA_SOBRECUPO, state.status());
        assertEquals(31, state.occupancy().people());
        assertNotNull(state.lastKalmanStep());
        assertNotNull(state.lastThermalDecision());
        assertEquals(1, fixture.telemetryRows.rows().size());
        assertEquals(31, fixture.telemetryRows.rows().get(0).peopleCount());
        assertEquals(1, fixture.actuator.commands.size());
        assertEquals(1, fixture.notifier.updates.size());
        assertEquals(1, fixture.notifier.traces.size());
    }

    @Test
    @DisplayName("La traza recorre la arquitectura de afuera hacia adentro y de vuelta afuera")
    void traceFollowsHexagonalOrder() {
        fixture.pipeline.process(frame(12, 700, 22), "MQTT · prueba");
        ArchitectureTrace trace = fixture.notifier.traces.get(0);
        List<HexagonalLayer> layers = trace.steps().stream().map(TraceStep::layer).toList();
        assertEquals(HexagonalLayer.INBOUND_ADAPTER, layers.get(0));
        assertEquals(HexagonalLayer.INPUT_PORT, layers.get(1));
        assertEquals(HexagonalLayer.USE_CASE, layers.get(2));
        assertTrue(layers.contains(HexagonalLayer.DOMAIN_SERVICE));
        assertEquals(HexagonalLayer.OUTBOUND_ADAPTER, layers.get(layers.size() - 1));
        assertTrue(trace.steps().stream().anyMatch(s -> s.component().equals("Actuator")));
    }

    @Test
    @DisplayName("Solo se envía un comando al HVAC cuando cambia la estrategia")
    void commandsOnlyOnChange() {
        fixture.pipeline.process(frame(12, 700, 22), "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(1));
        fixture.pipeline.process(frame(12, 700, 22), "MQTT · prueba");
        assertEquals(1, fixture.actuator.commands.size());
        assertEquals(2, fixture.notifier.updates.size());
    }

    @Test
    @DisplayName("El override docente gana sobre el controlador y se acumula el ahorro entre frames")
    void overrideAndSavings() {
        fixture.pipeline.process(frame(0, 430, 22), "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(10));
        SpaceTwinState blocked = fixture.pipeline.process(frame(0, 430, 22), "MQTT · prueba");
        assertEquals(HvacControlState.BLOQUEO_AHORRO_VACIO, blocked.hvacState());
        // 54 m² · 0.12 kW/m² = 6.48 kW · 10 min = 1.08 kWh (10:00 en Bogotá, dentro del horario)
        assertEquals(1.08, blocked.energySavedKwh(), 1e-6);

        SpaceTwinState overridden = fixture.controls.manualOverride("LAB-IA-102", Duration.ofMinutes(45));
        assertEquals(HvacControlState.OVERRIDE_MANUAL_DOCENTE, overridden.hvacState());
        assertEquals(2, fixture.actuator.commands.size());
    }

    private EnvironmentReading arduino(boolean lightsOn, int devicesOn) {
        return new EnvironmentReading("309-A", fixture.clock.instant(), 19.5, 52, null, lightsOn ? 400 : 20, lightsOn,
                devicesOn);
    }

    private static TelemetrySnapshot withTime(TelemetrySnapshot frame, Instant at) {
        return new TelemetrySnapshot(frame.spaceId(), at, frame.cameraOnline(), frame.detections(), frame.co2Ppm(),
                frame.indoorTemperatureC(), frame.outdoorTemperatureC(), frame.humidityPct(), frame.illuminanceLux(),
                frame.wifiDevices(), frame.activePowerKw(), frame.lightsOn(), frame.devicesOn());
    }

    private TelemetrySnapshot emptyRoomFrame() {
        return new TelemetrySnapshot("309-A", fixture.clock.instant(), true, List.of(), 430, 19.5, 13.5, 52, 400, 0, 1.0);
    }

    @Test
    @DisplayName("El Arduino reporta luces y equipos; se apagan solo tras 5 min de salón vacío")
    void idleShutdownAfterThreshold() {
        fixture.pipeline.process(emptyRoomFrame(), "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(2));
        SpaceTwinState wasting = fixture.environment.processEnvironment(arduino(true, 24), "MQTT · Arduino");
        assertEquals(RoomCondition.LUCES_Y_EQUIPOS_SIN_USO, wasting.condition());
        assertEquals(24, wasting.equipment().devicesOn());
        assertTrue(fixture.equipment.commands.isEmpty());

        fixture.clock.advance(Duration.ofMinutes(4));
        SpaceTwinState shutDown = fixture.environment.processEnvironment(arduino(true, 24), "MQTT · Arduino");
        assertEquals(1, fixture.equipment.commands.size());
        assertTrue(fixture.equipment.commands.get(0).suspendDevices());
        assertFalse(shutDown.equipment().lightsOn());
        assertEquals(RoomCondition.LIBRE, shutDown.condition());
        assertNotNull(shutDown.equipmentShutdownAt());

        // Someone switches the lights on again without staying: no second order until the threshold passes again.
        fixture.clock.advance(Duration.ofMinutes(1));
        fixture.environment.processEnvironment(arduino(true, 0), "MQTT · Arduino");
        assertEquals(1, fixture.equipment.commands.size());
        fixture.clock.advance(Duration.ofMinutes(5));
        fixture.environment.processEnvironment(arduino(true, 0), "MQTT · Arduino");
        assertEquals(2, fixture.equipment.commands.size());
        assertFalse(fixture.equipment.commands.get(1).suspendDevices());
    }

    @Test
    @DisplayName("Con personas dentro no hay orden de apagado y el salón deja de estar vacío")
    void occupiedRoomKeepsEquipment() {
        fixture.clock.advance(Duration.ofMinutes(20));
        TelemetrySnapshot occupied = new TelemetrySnapshot("309-A", fixture.clock.instant(), true,
                List.of(new TrackedDetection(1, DetectedObjectClass.PERSON, 0.9, new BoundingBox(0.1, 0.4, 0.14, 0.47))),
                600, 20, 13.5, 52, 400, 2, 1.5, true, 1);
        SpaceTwinState state = fixture.pipeline.process(occupied, "MQTT · prueba");
        assertEquals(RoomCondition.OCUPADO, state.condition());
        assertNull(state.emptySince());
        assertTrue(fixture.equipment.commands.isEmpty());
        assertTrue(fixture.notifier.traces.get(0).steps().stream().anyMatch(s -> s.component().equals("RoomConditionPolicy")));
    }

    @Test
    @DisplayName("Si otra instancia guarda el salón entre la lectura y la escritura, la decisión se rehace con el estado nuevo")
    void concurrentWriteIsRecomputed() {
        fixture.pipeline.process(frame(12, 700, 22), "MQTT · prueba");
        int[] calls = {0};
        SpaceTwinState stored = fixture.writer.replace(frame(12, 700, 22).spaceId(), () -> null, previous -> {
            if (calls[0]++ == 0) {
                // Another backend instance stores the room after we read it.
                fixture.twins.overwrite(previous.withControls(previous.controls().withMaintenance(true)));
            }
            return previous.withControls(previous.controls());
        });
        assertEquals(2, calls[0]);
        assertEquals(1, fixture.twins.conflicts);
        assertTrue(stored.maintenance());
    }

    @Test
    @DisplayName("La cámara y el Arduino del mismo salón en paralelo no se pisan")
    void parallelStreamsOfOneRoomTakeTurns() throws Exception {
        Instant start = fixture.clock.instant().minusSeconds(60);
        Thread camera = new Thread(() -> {
            for (int i = 0; i < 40; i++) {
                fixture.pipeline.process(withTime(emptyRoomFrame(), start.plusMillis(i)), "MQTT · cámara");
            }
        });
        Thread board = new Thread(() -> {
            for (int i = 0; i < 40; i++) {
                EnvironmentReading reading = arduino(true, 3);
                fixture.environment.processEnvironment(new EnvironmentReading(reading.spaceId(), start.plusMillis(i),
                        reading.temperatureC(), reading.humidityPct(), reading.co2Ppm(), reading.illuminanceLux(),
                        reading.lightsOn(), reading.devicesOn()), "MQTT · Arduino");
            }
        });
        camera.start();
        board.start();
        camera.join();
        board.join();
        assertEquals(0, fixture.twins.conflicts);
        assertEquals(80, fixture.notifier.updates.size());
    }

    @Test
    @DisplayName("Un frame repetido (reentrega MQTT QoS 1) no vuelve a aplicar el Kalman ni guarda otra lectura")
    void redeliveredFrameIsIgnored() {
        TelemetrySnapshot frame = frame(12, 700, 22);
        SpaceTwinState first = fixture.pipeline.process(frame, "MQTT · prueba");
        int updates = fixture.notifier.updates.size();

        SpaceTwinState again = fixture.pipeline.process(frame, "MQTT · prueba");

        assertEquals(first, again);
        assertEquals(updates, fixture.notifier.updates.size());
        assertEquals(1, fixture.telemetryRows.findRecent("LAB-IA-102", edu.campusnexo.domain.shared.ResultLimit.of(10)).size());
        assertEquals(List.of("camara:duplicada"), fixture.metrics.discarded);
    }

    @Test
    @DisplayName("Un frame que llega tarde (más viejo que el último) no retrocede el gemelo")
    void lateFrameIsIgnored() {
        fixture.pipeline.process(frame(12, 700, 22), "MQTT · prueba");
        TelemetrySnapshot late = withTime(frame(30, 1200, 25), fixture.clock.instant().minusSeconds(30));

        SpaceTwinState state = fixture.pipeline.process(late, "MQTT · prueba");

        assertEquals(12, state.occupancy().people());
        assertEquals(List.of("camara:fuera_de_orden"), fixture.metrics.discarded);
    }

    @Test
    @DisplayName("Un frame con fecha imposible se rechaza antes de tocar el gemelo o el historial")
    void invalidTimestampChangesNothing() {
        TelemetrySnapshot future = withTime(frame(12, 700, 22), fixture.clock.instant().plus(Duration.ofHours(1)));

        assertThrows(DomainValidationException.class, () -> fixture.pipeline.process(future, "MQTT · prueba"));

        assertTrue(fixture.twins.findTwin("LAB-IA-102").isEmpty());
        assertTrue(fixture.telemetryRows.findRecent("LAB-IA-102", edu.campusnexo.domain.shared.ResultLimit.of(10)).isEmpty());
    }

    @Test
    @DisplayName("El conteo propio de la cámara se guarda aparte de la estimación fusionada")
    void storesTheRawCameraCountApartFromTheEstimate() {
        fixture.pipeline.process(frame(12, 700, 22), "MQTT · prueba");
        var reading = fixture.telemetryRows.findLatest("LAB-IA-102").orElseThrow();
        assertEquals(12, reading.cameraPeople());
    }

    @Test
    @DisplayName("Pasto antes de clase: el salón vacío y frío se pre-calienta y el ESP32 recibe CALEFACCION")
    void preHeatsAColdRoomBeforeTheClass() {
        fixture.reservations.create(new CreateReservationCommand("AULA-301", fixture.clock.instant().plus(Duration.ofMinutes(20)),
                fixture.clock.instant().plus(Duration.ofMinutes(110)), 25, "IS-701"));
        TelemetrySnapshot coldAndEmpty = new TelemetrySnapshot("AULA-301", fixture.clock.instant(), true, List.of(), 430,
                18.2, 9.0, 60, 200, 0, 0.1);

        SpaceTwinState state = fixture.pipeline.process(coldAndEmpty, "MQTT · prueba");

        assertEquals(HvacControlState.PRE_CALENTAMIENTO_IA, state.hvacState());
        assertEquals(HvacMode.CALEFACCION, state.hvacMode());
        assertEquals(HvacMode.CALEFACCION, fixture.actuator.commands.getLast().mode());
    }

    @Test
    @DisplayName("Un espacio sin equipo de clima nunca se climatiza ni acumula ahorro")
    void roomWithoutUnitIsNeverClimatized() {
        TelemetrySnapshot frame = new TelemetrySnapshot("BIBLIOTECA-2B", fixture.clock.instant(), true, List.of(), 600,
                17.0, 9.0, 55, 300, 20, 1.0);
        SpaceTwinState first = fixture.pipeline.process(frame, "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(10));
        SpaceTwinState later = fixture.pipeline.process(withTime(frame, fixture.clock.instant()), "MQTT · prueba");

        assertEquals(HvacControlState.SIN_CLIMATIZACION, first.hvacState());
        assertEquals(0.0, later.energySavedKwh());
        assertTrue(fixture.actuator.commands.isEmpty(), "no hay equipo al que mandar órdenes");
    }

    @Test
    @DisplayName("La familia tropical deshumidifica un salón ocupado con 82 % de humedad aunque la temperatura esté bien")
    void tropicalRoomIsDehumidified() {
        List<TrackedDetection> people = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            people.add(new TrackedDetection(i, DetectedObjectClass.PERSON, 0.9, new BoundingBox(0.1, 0.1, 0.14, 0.17)));
        }
        TelemetrySnapshot humid = new TelemetrySnapshot("AULA-MAGNA-204", fixture.clock.instant(), true, people, 700,
                24.5, 29.0, 82, 300, 20, 2.0);

        SpaceTwinState state = fixture.pipeline.process(humid, "MQTT · prueba");

        assertEquals(HvacMode.DESHUMIDIFICACION, state.hvacMode());
        assertTrue(state.hvac().reason().contains("Humedad"));
    }
}
