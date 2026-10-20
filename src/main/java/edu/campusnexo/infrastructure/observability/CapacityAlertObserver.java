package edu.campusnexo.infrastructure.observability;

import edu.campusnexo.application.event.TelemetryObserver;
import edu.campusnexo.domain.event.TelemetryRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class CapacityAlertObserver implements TelemetryObserver {
    private static final Logger log = LoggerFactory.getLogger(CapacityAlertObserver.class);
    @Override public void onTelemetryRecorded(TelemetryRecorded event) {
        int occupancy = event.telemetry().peopleCount();
        int capacity = event.space().capacity();
        if (occupancy > capacity) {
            log.warn("Alerta de sobrecupo: spaceId={}, occupancy={}, capacity={}",
                    event.space().spaceId(), occupancy, capacity);
        }
    }
}
