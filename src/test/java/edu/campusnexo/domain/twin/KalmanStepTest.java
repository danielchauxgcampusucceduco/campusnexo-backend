package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class KalmanStepTest {

    @Test
    void keepsAnImmutableSnapshotOfTheMeasurements() {
        var measurements = new ArrayList<KalmanMeasurement>();
        measurements.add(new KalmanMeasurement(
                KalmanMeasurement.MeasurementSource.CAMARA_YOLO, 8, 1, 0.5));
        var belief = new OccupancyBelief(8, 1, Instant.EPOCH);
        KalmanStep step = new KalmanStep(belief, 1, 2, measurements, belief);
        measurements.clear();

        assertEquals(1, step.measurements().size());
        assertEquals(8, step.posterior().people());
    }
}
