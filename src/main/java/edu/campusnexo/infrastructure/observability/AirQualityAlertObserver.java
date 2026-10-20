package edu.campusnexo.infrastructure.observability;

import edu.campusnexo.application.event.TelemetryObserver;
import edu.campusnexo.domain.event.TelemetryRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class AirQualityAlertObserver implements TelemetryObserver {
    private static final Logger log = LoggerFactory.getLogger(AirQualityAlertObserver.class);
    private static final double CO2_ALERT_THRESHOLD_PPM = 1000.0;
    @Override public void onTelemetryRecorded(TelemetryRecorded event) {
        double co2 = event.telemetry().co2Ppm();
        if (co2 > CO2_ALERT_THRESHOLD_PPM) {
            log.warn("Alerta CO2: spaceId={}, reading={} ppm, threshold={} ppm",
                    event.space().spaceId(), co2, CO2_ALERT_THRESHOLD_PPM);
        }
    }
}
