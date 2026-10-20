package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.trace.TraceStep;
import edu.campusnexo.domain.twin.HvacControlState;
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
}
