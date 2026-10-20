package edu.campusnexo.domain.twin;

import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.twin.KalmanMeasurement.MeasurementSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * One-dimensional Kalman filter that fuses the camera count, a CO2 mass-balance estimate and the
 * Wi-Fi device count into a single occupancy belief. Random-walk model: occupancy is assumed constant
 * between frames, with uncertainty growing linearly with the elapsed time. Pure and stateless.
 */
public final class OccupancyKalmanDomainService {
    private static final double MIN_MEASUREMENT_VARIANCE = 1.0;

    private final KalmanParameters parameters;

    public OccupancyKalmanDomainService(KalmanParameters parameters) {
        this.parameters = parameters;
    }

    public KalmanParameters parameters() {
        return parameters;
    }

    public KalmanStep update(OccupancyBelief previous, TelemetrySnapshot snapshot, CampusSpace space) {
        double minutes = Math.max(0, Duration.between(previous.at(), snapshot.capturedAt()).toMillis() / 60_000.0);
        double estimate = previous.estimate();
        double variance = previous.variance() + parameters.processNoisePerMinute() * minutes;
        double predictedVariance = variance;

        List<KalmanMeasurement> fused = new ArrayList<>();
        for (Observation observation : observations(snapshot, space)) {
            double gain = variance / (variance + observation.variance());
            estimate = estimate + gain * (observation.value() - estimate);
            variance = (1 - gain) * variance;
            fused.add(new KalmanMeasurement(observation.source(), observation.value(), observation.variance(), gain));
        }
        double bounded = Math.min(Math.max(0, estimate), space.capacity() * 3.0);
        OccupancyBelief posterior = new OccupancyBelief(bounded, Math.max(variance, 1e-6), snapshot.capturedAt());
        return new KalmanStep(previous, minutes, predictedVariance, fused, posterior);
    }

    private List<Observation> observations(TelemetrySnapshot snapshot, CampusSpace space) {
        List<Observation> observations = new ArrayList<>(3);
        Integer camera = snapshot.cameraPeopleCount();
        if (camera != null) {
            observations.add(new Observation(MeasurementSource.CAMARA_YOLO, camera, parameters.cameraVariance()));
        }
        double co2Excess = Math.max(0, snapshot.co2Ppm() - parameters.outdoorCo2Ppm());
        double fromCo2 = co2Excess * space.areaSqm() / parameters.co2PpmPerPersonSqm();
        observations.add(new Observation(MeasurementSource.CO2_BALANCE_MASICO, fromCo2,
                relativeVariance(fromCo2, parameters.co2RelativeError())));
        double fromWifi = snapshot.wifiDevices() * parameters.peoplePerDevice();
        observations.add(new Observation(MeasurementSource.WIFI_DISPOSITIVOS, fromWifi,
                relativeVariance(fromWifi, parameters.wifiRelativeError())));
        return observations;
    }

    private static double relativeVariance(double value, double relativeError) {
        double sigma = relativeError * value + 2.0;
        return Math.max(MIN_MEASUREMENT_VARIANCE, sigma * sigma);
    }

    private record Observation(MeasurementSource source, double value, double variance) {
    }
}
