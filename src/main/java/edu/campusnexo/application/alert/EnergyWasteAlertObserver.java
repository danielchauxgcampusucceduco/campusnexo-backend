package edu.campusnexo.application.alert;

import edu.campusnexo.application.port.out.TelemetryObserver;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;

public class EnergyWasteAlertObserver implements TelemetryObserver {
    private final AlertRecorder recorder;
    private final AlertThresholds thresholds;

    public EnergyWasteAlertObserver(AlertRecorder recorder, AlertThresholds thresholds) {
        this.recorder = recorder;
        this.thresholds = thresholds;
    }

    @Override
    public void onTelemetryRecorded(TelemetryRecorded event) {
        Telemetry reading = event.telemetry();
        double limit = thresholds.emptySpacePowerKw();
        if (!reading.isEmpty() || reading.activePowerKw() <= limit) {
            return;
        }
        Alert alert = Alert.raise(reading.spaceId(), AlertType.CONSUMO_ESPACIO_VACIO, AlertSeverity.ADVERTENCIA,
                reading.recordedAt(), "Espacio vacío con " + reading.activePowerKw() + " kW de consumo activo",
                reading.activePowerKw(), limit);
        recorder.recordUnlessRaisedSince(alert, reading.recordedAt().minus(thresholds.cooldown()));
    }
}
