package edu.campusnexo.application.telemetry;

import edu.campusnexo.domain.telemetry.TelemetryRecorded;

/** Output port for reporting failures from telemetry observers. */
public interface ObserverFailureReporter {
    void report(TelemetryObserver observer, TelemetryRecorded event, RuntimeException failure);
}
