package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessSpaceTelemetryServiceTest {
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

    private TelemetrySnapshot emptyRoomFrame() {
        return new TelemetrySnapshot("309-A", fixture.clock.instant(), true, List.of(), 430, 19.5, 13.5, 52, 400, 0, 1.0);
    }

    @Test
    @DisplayName("El Arduino reporta luces y equipos; se apagan solo tras 5 min de salón vacío")
    void idleShutdownAfterThreshold() {
        fixture.pipeline.process(emptyRoomFrame(), "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(2));
        SpaceTwinState wasting = fixture.pipeline.processEnvironment(arduino(true, 24), "MQTT · Arduino");
        assertEquals(RoomCondition.LUCES_Y_EQUIPOS_SIN_USO, wasting.condition());
        assertEquals(24, wasting.equipment().devicesOn());
        assertTrue(fixture.equipment.commands.isEmpty());

        fixture.clock.advance(Duration.ofMinutes(4));
        SpaceTwinState shutDown = fixture.pipeline.processEnvironment(arduino(true, 24), "MQTT · Arduino");
        assertEquals(1, fixture.equipment.commands.size());
        assertTrue(fixture.equipment.commands.get(0).suspendDevices());
        assertFalse(shutDown.equipment().lightsOn());
        assertEquals(RoomCondition.LIBRE, shutDown.condition());
        assertNotNull(shutDown.equipmentShutdownAt());

        // Someone switches the lights on again without staying: no second order until the threshold passes again.
        fixture.clock.advance(Duration.ofMinutes(1));
        fixture.pipeline.processEnvironment(arduino(true, 0), "MQTT · Arduino");
        assertEquals(1, fixture.equipment.commands.size());
        fixture.clock.advance(Duration.ofMinutes(5));
        fixture.pipeline.processEnvironment(arduino(true, 0), "MQTT · Arduino");
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
}
