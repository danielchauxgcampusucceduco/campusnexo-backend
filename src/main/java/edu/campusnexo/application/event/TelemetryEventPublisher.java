package edu.campusnexo.application.event;

import edu.campusnexo.domain.event.TelemetryRecorded;
import java.util.List;

/** Observer subject: notifies independent consumers after a telemetry reading is stored. */
public class TelemetryEventPublisher {
    private final List<TelemetryObserver> observers;
    private final ObserverFailureReporter failureReporter;

    public TelemetryEventPublisher(List<TelemetryObserver> observers, ObserverFailureReporter failureReporter) {
        this.observers = List.copyOf(observers);
        this.failureReporter = failureReporter;
    }

    public void publish(TelemetryRecorded event) {
        for (TelemetryObserver observer : observers) {
            try { observer.onTelemetryRecorded(event); }
            catch (RuntimeException ex) {
                // A notification failure must not make a successfully persisted reading appear rejected.
                failureReporter.report(observer, event, ex);
            }
        }
    }
}
