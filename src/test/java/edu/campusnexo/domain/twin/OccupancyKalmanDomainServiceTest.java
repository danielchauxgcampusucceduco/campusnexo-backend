package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.TrackedDetection;
import edu.campusnexo.support.TestData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OccupancyKalmanDomainServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-20T15:00:00Z");
    private final OccupancyKalmanDomainService kalman = new OccupancyKalmanDomainService(KalmanParameters.defaults());

    private static List<TrackedDetection> people(int count) {
        List<TrackedDetection> detections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            detections.add(new TrackedDetection(i, DetectedObjectClass.PERSON, 0.9, new BoundingBox(0.1, 0.1, 0.15, 0.2)));
        }
        return detections;
    }

    private static TelemetrySnapshot snapshot(Instant at, boolean camera, int people, double co2, int wifi) {
        return new TelemetrySnapshot("AULA-301", at, camera, people(people), co2, 22, 14.0, 55, 300, wifi, 2);
    }

    @Test
    @DisplayName("La cámara, con menor varianza, domina la fusión con CO2 y Wi-Fi")
    void cameraDominates() {
        OccupancyBelief prior = OccupancyBelief.uninformed(T0, 32);
        // CO2: (820 − 420)·48/1000 = 19.2 ; Wi-Fi: 22·0.8 = 17.6 ; cámara: 24
        KalmanStep step = kalman.update(prior, snapshot(T0.plusSeconds(60), true, 24, 820, 22), TestData.andeanClassroom());
        assertEquals(3, step.measurements().size());
        assertEquals(MeasurementSource.CAMARA_YOLO, step.measurements().get(0).source());
        assertTrue(Math.abs(step.posterior().estimate() - 24) < 1.0, "estimación " + step.posterior().estimate());
        assertTrue(step.posterior().variance() < KalmanParameters.defaults().cameraVariance());
    }

    @Test
    @DisplayName("Con la cámara caída se usan CO2 y Wi-Fi, con mayor incertidumbre")
    void cameraOfflineIncreasesUncertainty() {
        OccupancyBelief prior = OccupancyBelief.uninformed(T0, 32);
        KalmanStep online = kalman.update(prior, snapshot(T0, true, 18, 820, 22), TestData.andeanClassroom());
        KalmanStep offline = kalman.update(prior, snapshot(T0, false, 18, 820, 22), TestData.andeanClassroom());
        assertEquals(2, offline.measurements().size());
        assertTrue(offline.posterior().variance() > online.posterior().variance());
        double estimate = offline.posterior().estimate();
        // Sin cámara la estimación queda entre las señales indirectas (CO2: 19.2 ; Wi-Fi: 17.6) y el prior vacío.
        assertTrue(estimate > 15 && estimate < 19.2, "estimación " + estimate);
    }

    @Test
    @DisplayName("La predicción aumenta la varianza con el tiempo transcurrido")
    void processNoiseGrowsWithTime() {
        OccupancyBelief prior = new OccupancyBelief(10, 1.0, T0);
        KalmanStep step = kalman.update(prior, snapshot(T0.plusSeconds(600), true, 10, 650, 12), TestData.andeanClassroom());
        assertEquals(10.0, step.elapsedMinutes());
        assertEquals(1.0 + 0.8 * 10, step.predictedVariance(), 1e-9);
    }
}
