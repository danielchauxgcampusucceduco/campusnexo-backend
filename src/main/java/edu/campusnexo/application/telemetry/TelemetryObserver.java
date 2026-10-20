package edu.campusnexo.application.telemetry;

import edu.campusnexo.domain.telemetry.TelemetryRecorded;

/** Observer: reacts to a reading after it has been stored. */
public interface TelemetryObserver {
    void onTelemetryRecorded(TelemetryRecorded event);
}
