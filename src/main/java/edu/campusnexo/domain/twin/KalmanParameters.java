package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

/**
 * Noise model of the occupancy filter (illustrative values, to be tuned with campus data).
 *
 * @param processNoisePerMinute variance added per minute: people enter and leave between frames
 * @param cameraVariance        variance of the camera count (occlusions, false negatives)
 * @param wifiRelativeError     relative standard deviation of the Wi-Fi estimate
 * @param peoplePerDevice       people per connected device
 */
public record KalmanParameters(
        double processNoisePerMinute,
        double cameraVariance,
        double wifiRelativeError,
        double peoplePerDevice) {

    public KalmanParameters {
        require(Double.isFinite(processNoisePerMinute) && Double.isFinite(cameraVariance)
                && Double.isFinite(wifiRelativeError) && Double.isFinite(peoplePerDevice)
                && processNoisePerMinute > 0 && cameraVariance > 0 && wifiRelativeError > 0 && peoplePerDevice > 0,
                "Los parámetros del filtro de Kalman deben ser positivos");
    }

    public static KalmanParameters defaults() {
        return new KalmanParameters(0.8, 0.64, 0.30, 0.8);
    }
}
