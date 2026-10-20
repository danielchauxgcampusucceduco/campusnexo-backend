package edu.campusnexo.application.event;

import edu.campusnexo.domain.event.TelemetryRecorded;

/** Output port for reporting failures from telemetry observers. */
public interface ObserverFailureReporter {
    void report(TelemetryObserver observer, TelemetryRecorded event, RuntimeException failure);
}
