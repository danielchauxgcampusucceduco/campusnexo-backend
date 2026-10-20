package edu.campusnexo.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.twin.*} settings of the digital twin. Defaults live in {@code application.yml}; all values
 * are illustrative and must be calibrated with campus measurements.
 */
@ConfigurationProperties(prefix = "app.twin")
record TwinProperties(Kalman kalman, Thermal thermal, Status status, Energy energy, int traceHistory) {

    record Kalman(double processNoisePerMinute, double cameraVariance, double co2RelativeError, double wifiRelativeError,
                  double peoplePerDevice, double outdoorCo2Ppm, double co2PpmPerPersonSqm) {
    }

    record Thermal(double timeConstantMinutes, double occupantGainCPerHour, double hvacRateCPerMinute, int stepMinutes,
                   int defaultOccupiedMinutes, int preConditioningLeadMinutes, int ecoSetbackWindowMinutes,
                   double ecoSetbackOffsetC, double co2VentilationPpm) {
    }

    record Status(Duration noShowGrace, double underuseRatio, Duration deskAbandonThreshold) {
    }

    record Energy(double ratedKwPerSqm, Duration maxReadingInterval, int scheduleStartHour, int scheduleEndHour,
                  String timeZone, double emissionFactorKgCo2PerKwh) {
    }
}
