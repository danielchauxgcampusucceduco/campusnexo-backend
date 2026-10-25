package edu.campusnexo.domain.twin;

/** One sensor reading fused by the filter, with the Kalman gain it received. */
public record KalmanMeasurement(MeasurementSource source, double value, double variance, double gain) {
    public enum MeasurementSource {
        CAMARA_YOLO,
        CO2_BALANCE_MASICO,
        WIFI_DISPOSITIVOS
    }
}
