package edu.campusnexo.application.telemetry;

import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import java.util.List;

/**
 * Observer subject: notifies independent observers after a reading is stored. Notification is
 * synchronous; a failing observer is reported and does not affect the others or the stored reading.
 */
public class TelemetryEventPublisher {
    private final List<TelemetryObserver> observers;
    private final ObserverFailureReporter failureReporter;

    public TelemetryEventPublisher(List<TelemetryObserver> observers, ObserverFailureReporter failureReporter) {
        this.observers = List.copyOf(observers);
        this.failureReporter = failureReporter;
    }

    public void publish(TelemetryRecorded event) {
        for (TelemetryObserver observer : observers) {
            try {
                observer.onTelemetryRecorded(event);
            } catch (RuntimeException failure) {
                failureReporter.report(observer, event, failure);
            }
        }
    }

    public int observerCount() {
        return observers.size();
    }
}
