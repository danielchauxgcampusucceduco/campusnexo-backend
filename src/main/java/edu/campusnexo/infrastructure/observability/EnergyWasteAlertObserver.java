package edu.campusnexo.infrastructure.observability;

import edu.campusnexo.application.event.TelemetryObserver;
import edu.campusnexo.domain.event.TelemetryRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Demonstration alert only; its threshold must be calibrated with the campus energy baseline. */
@Component
class EnergyWasteAlertObserver implements TelemetryObserver {
    private static final Logger log = LoggerFactory.getLogger(EnergyWasteAlertObserver.class);
    private final double emptySpacePowerThresholdKw;

    EnergyWasteAlertObserver(
            @Value("${app.alerts.empty-space-power-threshold-kw:0.15}") double emptySpacePowerThresholdKw) {
        this.emptySpacePowerThresholdKw = emptySpacePowerThresholdKw;
    }

    @Override
    public void onTelemetryRecorded(TelemetryRecorded event) {
        double powerKw = event.telemetry().activePowerKw();
        if (event.telemetry().peopleCount() == 0 && powerKw > emptySpacePowerThresholdKw) {
            log.warn("Consumo detectado en espacio vacío: spaceId={}, power={} kW, threshold={} kW",
                    event.space().spaceId(), powerKw, emptySpacePowerThresholdKw);
        }
    }
}
