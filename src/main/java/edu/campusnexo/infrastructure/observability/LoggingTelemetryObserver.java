package edu.campusnexo.infrastructure.observability;

import edu.campusnexo.application.telemetry.TelemetryObserver;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Infrastructure observer: shows that new observers plug in without touching the publisher. */
@Component
class LoggingTelemetryObserver implements TelemetryObserver {
    private static final Logger log = LoggerFactory.getLogger(LoggingTelemetryObserver.class);

    @Override
    public void onTelemetryRecorded(TelemetryRecorded event) {
        log.debug("Lectura registrada: spaceId={}, recordedAt={}, people={}", event.space().spaceId(),
                event.telemetry().recordedAt(), event.telemetry().peopleCount());
    }
}
