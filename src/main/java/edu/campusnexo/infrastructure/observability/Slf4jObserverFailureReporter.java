package edu.campusnexo.infrastructure.observability;

import edu.campusnexo.application.event.ObserverFailureReporter;
import edu.campusnexo.application.event.TelemetryObserver;
import edu.campusnexo.domain.event.TelemetryRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class Slf4jObserverFailureReporter implements ObserverFailureReporter {
    private static final Logger log = LoggerFactory.getLogger(Slf4jObserverFailureReporter.class);

    @Override
    public void report(TelemetryObserver observer, TelemetryRecorded event, RuntimeException failure) {
        log.error("Observer {} falló para el espacio {}", observer.getClass().getSimpleName(),
                event.space().spaceId(), failure);
    }
}
