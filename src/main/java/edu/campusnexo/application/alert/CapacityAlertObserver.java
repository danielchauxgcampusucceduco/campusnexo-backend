package edu.campusnexo.application.alert;

import edu.campusnexo.application.port.out.TelemetryObserver;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;

public class CapacityAlertObserver implements TelemetryObserver {
    private final AlertRecorder recorder;
    private final AlertThresholds thresholds;

    public CapacityAlertObserver(AlertRecorder recorder, AlertThresholds thresholds) {
        this.recorder = recorder;
        this.thresholds = thresholds;
    }

    @Override
    public void onTelemetryRecorded(TelemetryRecorded event) {
        Telemetry reading = event.telemetry();
        int capacity = event.space().capacity();
        if (reading.peopleCount() <= capacity) {
            return;
        }
        // More people than seats is a safety matter (evacuation): always critical.
        AlertSeverity severity = AlertSeverity.CRITICA;
        Alert alert = Alert.raise(reading.spaceId(), AlertType.SOBRECUPO, severity, reading.recordedAt(),
                reading.peopleCount() + " personas en un espacio con aforo de " + capacity,
                reading.peopleCount(), capacity);
        recorder.recordUnlessRaisedSince(alert, reading.recordedAt().minus(thresholds.cooldown()));
    }
}
