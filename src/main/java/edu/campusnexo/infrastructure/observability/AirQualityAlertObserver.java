package edu.campusnexo.infrastructure.observability;

import edu.campusnexo.application.event.TelemetryObserver;
import edu.campusnexo.domain.event.TelemetryRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class AirQualityAlertObserver implements TelemetryObserver {
    private static final Logger log = LoggerFactory.getLogger(AirQualityAlertObserver.class);
    private final double co2AlertThresholdPpm;

    AirQualityAlertObserver(@Value("${app.alerts.co2-threshold-ppm:1000}") double co2AlertThresholdPpm) {
        this.co2AlertThresholdPpm = co2AlertThresholdPpm;
    }
    @Override public void onTelemetryRecorded(TelemetryRecorded event) {
        double co2 = event.telemetry().co2Ppm();
        if (co2 > co2AlertThresholdPpm) {
            log.warn("Alerta CO2: spaceId={}, reading={} ppm, threshold={} ppm",
                    event.space().spaceId(), co2, co2AlertThresholdPpm);
        }
    }
}
