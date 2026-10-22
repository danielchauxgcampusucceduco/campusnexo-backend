package edu.campusnexo.application.port.out;

import edu.campusnexo.domain.telemetry.TelemetryRecorded;

/** Observer: reacts to a reading after it has been stored. */
public interface TelemetryObserver {
    void onTelemetryRecorded(TelemetryRecorded event);
}
