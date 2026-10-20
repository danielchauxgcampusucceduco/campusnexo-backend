package edu.campusnexo.domain.twin;

import java.util.List;

/**
 * Full trace of one filter cycle, kept so the dashboard can show the equations with real numbers:
 * predict {@code x⁻ = x, P⁻ = P + Q·Δt}; for each measurement {@code K = P⁻/(P⁻ + R)},
 * {@code x = x⁻ + K(z − x⁻)}, {@code P = (1 − K)P⁻}.
 */
public record KalmanStep(
        OccupancyBelief previous,
        double elapsedMinutes,
        double predictedVariance,
        List<KalmanMeasurement> measurements,
        OccupancyBelief posterior) {

    public KalmanStep {
        measurements = List.copyOf(measurements);
    }
}
