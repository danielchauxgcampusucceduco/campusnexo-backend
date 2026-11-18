package edu.campusnexo.application.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.ResultLimit;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.trace.TraceStep;
import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
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

    private TelemetrySnapshot frame(String spaceId, int people, Instant at) {
        List<TrackedDetection> detections = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            detections.add(new TrackedDetection(i, DetectedObjectClass.PERSON, 0.9, new BoundingBox(0.1, 0.4, 0.14, 0.47)));
        }
        return new TelemetrySnapshot(spaceId, at, true, detections);
    }

    private TelemetrySnapshot frame(int people) {
        return frame("LAB-IA-102", people, fixture.clock.instant());
    }

    @Test
    @DisplayName("Un frame de la cámara actualiza el gemelo, guarda la lectura y publica el cambio y la traza")
    void processesFrameEndToEnd() {
        SpaceTwinState state = fixture.pipeline.process(frame(31), "MQTT · prueba");

        assertEquals(SpaceStatus.ALERTA_SOBRECUPO, state.status());
        assertEquals(31, state.occupancy().people());
        assertNotNull(state.lastKalmanStep());
        assertEquals(1, fixture.telemetryRows.rows().size());
        assertEquals(31, fixture.telemetryRows.rows().get(0).peopleCount());
        assertEquals(1, fixture.notifier.updates.size());
        assertEquals(1, fixture.notifier.traces.size());
    }

    @Test
    @DisplayName("Sin Wi-Fi, el Kalman fusiona solo la cámara; con Wi-Fi, las dos fuentes")
    void kalmanUsesTheCameraAndTheWifiWhenThereIsOne() {
        SpaceTwinState camera = fixture.pipeline.process(frame(12), "MQTT · prueba");
        assertEquals(List.of(MeasurementSource.CAMARA_YOLO),
                camera.lastKalmanStep().measurements().stream().map(m -> m.source()).toList());

        fixture.clock.advance(Duration.ofMinutes(1));
        TelemetrySnapshot withWifi = new TelemetrySnapshot("LAB-IA-102", fixture.clock.instant(), true,
                frame(12).detections(), 14, false);
        SpaceTwinState both = fixture.pipeline.process(withWifi, "MQTT · prueba");
        assertEquals(List.of(MeasurementSource.CAMARA_YOLO, MeasurementSource.WIFI_DISPOSITIVOS),
                both.lastKalmanStep().measurements().stream().map(m -> m.source()).toList());
    }

    @Test
    @DisplayName("Con la cámara caída el gemelo conserva la última estimación y su margen de error crece")
    void cameraDownKeepsTheEstimateWithAGrowingMargin() {
        SpaceTwinState seen = fixture.pipeline.process(frame(12), "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(10));
        SpaceTwinState blind = fixture.pipeline.process(
                new TelemetrySnapshot("LAB-IA-102", fixture.clock.instant(), false, List.of()), "MQTT · prueba");

        assertEquals(12, blind.occupancy().people());
        assertTrue(blind.lastKalmanStep().measurements().isEmpty());
        assertTrue(blind.occupancy().variance() > seen.occupancy().variance());
        assertTrue(!blind.cameraOnline());
    }

    @Test
    @DisplayName("Mide cuánto tarda un frame del borde en llegar al gemelo publicado (QA-2)")
    void measuresFrameLatency() {
        TelemetrySnapshot captured = frame(10);
        fixture.clock.advance(Duration.ofMillis(800));

        fixture.pipeline.process(captured, "HTTPS · prueba");
        fixture.pipeline.process(captured, "HTTPS · prueba");

        assertEquals(List.of(Duration.ofMillis(800)), fixture.metrics.frameLatencies,
                "el frame repetido se descarta y no cuenta");
    }

    @Test
    @DisplayName("La traza recorre la arquitectura de afuera hacia adentro y de vuelta afuera")
    void traceFollowsHexagonalOrder() {
        fixture.pipeline.process(frame(12), "MQTT · prueba");
        ArchitectureTrace trace = fixture.notifier.traces.get(0);
        List<HexagonalLayer> layers = trace.steps().stream().map(TraceStep::layer).toList();
        assertEquals(HexagonalLayer.INBOUND_ADAPTER, layers.get(0));
        assertEquals(HexagonalLayer.INPUT_PORT, layers.get(1));
        assertEquals(HexagonalLayer.USE_CASE, layers.get(2));
        assertTrue(layers.contains(HexagonalLayer.DOMAIN_SERVICE));
        assertTrue(layers.contains(HexagonalLayer.OUTPUT_PORT));
        assertEquals(HexagonalLayer.OUTBOUND_ADAPTER, layers.get(layers.size() - 1));
        assertTrue(trace.steps().stream().anyMatch(s -> s.component().equals("Notifier")));
    }

    @Test
    @DisplayName("Con personas el salón deja de estar vacío; sin nadie se recuerda desde cuándo")
    void tracksSinceWhenTheRoomIsEmpty() {
        SpaceTwinState occupied = fixture.pipeline.process(frame(3), "MQTT · prueba");
        assertNull(occupied.emptySince());

        fixture.clock.advance(Duration.ofMinutes(5));
        Instant leftAt = fixture.clock.instant();
        fixture.pipeline.process(frame(0), "MQTT · prueba");
        fixture.clock.advance(Duration.ofMinutes(5));
        SpaceTwinState empty = fixture.pipeline.process(frame(0), "MQTT · prueba");
        assertEquals(SpaceStatus.LIBRE, empty.status());
        assertEquals(leftAt, empty.emptySince());
    }

    @Test
    @DisplayName("Si otra instancia guarda el salón entre la lectura y la escritura, la decisión se rehace con el estado nuevo")
    void concurrentWriteIsRecomputed() {
        fixture.pipeline.process(frame(12), "MQTT · prueba");
        int[] calls = {0};
        SpaceTwinState stored = fixture.writer.replace("LAB-IA-102", () -> null, previous -> {
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
    @DisplayName("Dos flujos de frames del mismo salón en paralelo no se pisan")
    void parallelStreamsOfOneRoomTakeTurns() throws Exception {
        Instant start = fixture.clock.instant().minusSeconds(60);
        Thread first = new Thread(() -> {
            for (int i = 0; i < 40; i++) {
                fixture.pipeline.process(frame("309-A", 0, start.plusMillis(2L * i)), "MQTT · cámara 1");
            }
        });
        Thread second = new Thread(() -> {
            for (int i = 0; i < 40; i++) {
                fixture.pipeline.process(frame("309-A", 1, start.plusMillis(2L * i + 1)), "MQTT · cámara 2");
            }
        });
        first.start();
        second.start();
        first.join();
        second.join();
        assertEquals(0, fixture.twins.conflicts);
        assertTrue(fixture.notifier.updates.size() >= 40, "cada flujo avanza; los frames más viejos se descartan");
    }

    @Test
    @DisplayName("Un frame repetido (reentrega MQTT QoS 1) no vuelve a aplicar el Kalman ni guarda otra lectura")
    void redeliveredFrameIsIgnored() {
        TelemetrySnapshot frame = frame(12);
        SpaceTwinState first = fixture.pipeline.process(frame, "MQTT · prueba");
        int updates = fixture.notifier.updates.size();

        SpaceTwinState again = fixture.pipeline.process(frame, "MQTT · prueba");

        assertEquals(first, again);
        assertEquals(updates, fixture.notifier.updates.size());
        assertEquals(1, fixture.telemetryRows.findRecent("LAB-IA-102", ResultLimit.of(10)).size());
        assertEquals(List.of("camara:duplicada"), fixture.metrics.discarded);
    }

    @Test
    @DisplayName("Un frame que llega tarde (más viejo que el último) no retrocede el gemelo")
    void lateFrameIsIgnored() {
        fixture.pipeline.process(frame(12), "MQTT · prueba");
        TelemetrySnapshot late = frame("LAB-IA-102", 30, fixture.clock.instant().minusSeconds(30));

        SpaceTwinState state = fixture.pipeline.process(late, "MQTT · prueba");

        assertEquals(12, state.occupancy().people());
        assertEquals(List.of("camara:fuera_de_orden"), fixture.metrics.discarded);
    }

    @Test
    @DisplayName("Un frame con fecha imposible se rechaza antes de tocar el gemelo o el historial")
    void invalidTimestampChangesNothing() {
        TelemetrySnapshot future = frame("LAB-IA-102", 12, fixture.clock.instant().plus(Duration.ofHours(1)));

        assertThrows(DomainValidationException.class, () -> fixture.pipeline.process(future, "MQTT · prueba"));

        assertTrue(fixture.twins.findTwin("LAB-IA-102").isEmpty());
        assertTrue(fixture.telemetryRows.findRecent("LAB-IA-102", ResultLimit.of(10)).isEmpty());
    }

    @Test
    @DisplayName("El conteo propio de la cámara se guarda aparte de la estimación fusionada")
    void storesTheRawCameraCountApartFromTheEstimate() {
        fixture.pipeline.process(frame(12), "MQTT · prueba");
        var reading = fixture.telemetryRows.findLatest("LAB-IA-102").orElseThrow();
        assertEquals(12, reading.cameraPeople());
        assertNull(reading.wifiDevices());
    }
}
