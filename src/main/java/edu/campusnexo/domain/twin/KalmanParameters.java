package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

/**
 * Noise model of the occupancy filter (illustrative values, to be tuned with campus data).
 *
 * @param processNoisePerMinute variance added per minute: people enter and leave between frames
 * @param cameraVariance        variance of the camera count (occlusions, false negatives)
 * @param co2RelativeError      relative standard deviation of the CO2 mass-balance estimate
 * @param wifiRelativeError     relative standard deviation of the Wi-Fi estimate
 * @param peoplePerDevice       people per connected device
 * @param outdoorCo2Ppm         background CO2 level
 * @param co2PpmPerPersonSqm    steady-state CO2 rise per person per square metre of floor
 */
public record KalmanParameters(
        double processNoisePerMinute,
        double cameraVariance,
        double co2RelativeError,
        double wifiRelativeError,
        double peoplePerDevice,
        double outdoorCo2Ppm,
        double co2PpmPerPersonSqm) {

    public KalmanParameters {
        require(processNoisePerMinute > 0 && cameraVariance > 0 && co2RelativeError > 0 && wifiRelativeError > 0
                && peoplePerDevice > 0 && outdoorCo2Ppm > 0 && co2PpmPerPersonSqm > 0,
                "Los parámetros del filtro de Kalman deben ser positivos");
    }

    public static KalmanParameters defaults() {
        return new KalmanParameters(0.8, 0.64, 0.35, 0.30, 0.8, 420, 1000);
    }
}
