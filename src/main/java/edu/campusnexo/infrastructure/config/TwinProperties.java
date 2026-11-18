package edu.campusnexo.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.twin.*} settings of the digital twin. Defaults live in {@code application.yml}; all values
 * are illustrative and must be calibrated with campus measurements. {@code timeZone} is the campus zone, used to
 * read the academic timetable and the hours of the day.
 */
@ConfigurationProperties(prefix = "app.twin")
record TwinProperties(Kalman kalman, Status status, String timeZone, int traceHistory) {

    record Kalman(double processNoisePerMinute, double cameraVariance, double wifiRelativeError, double peoplePerDevice) {
    }

    record Status(Duration noShowGrace, double underuseRatio, Duration deskAbandonThreshold) {
    }
}
