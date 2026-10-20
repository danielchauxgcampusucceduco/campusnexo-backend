package edu.campusnexo.application.alert;

import edu.campusnexo.application.telemetry.TelemetryObserver;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;

public class AirQualityAlertObserver implements TelemetryObserver {
    private final AlertRecorder recorder;
    private final AlertThresholds thresholds;

    public AirQualityAlertObserver(AlertRecorder recorder, AlertThresholds thresholds) {
        this.recorder = recorder;
        this.thresholds = thresholds;
    }

    @Override
    public void onTelemetryRecorded(TelemetryRecorded event) {
        Telemetry reading = event.telemetry();
        double limit = thresholds.co2Ppm();
        if (reading.co2Ppm() <= limit) {
            return;
        }
        AlertSeverity severity = reading.co2Ppm() > limit * 1.5 ? AlertSeverity.CRITICA : AlertSeverity.ADVERTENCIA;
        Alert alert = Alert.raise(reading.spaceId(), AlertType.CO2_ALTO, severity, reading.recordedAt(),
                "CO2 de " + Math.round(reading.co2Ppm()) + " ppm supera el umbral de " + Math.round(limit) + " ppm",
                reading.co2Ppm(), limit);
        recorder.recordUnlessRaisedSince(alert, reading.recordedAt().minus(thresholds.cooldown()));
    }
}
