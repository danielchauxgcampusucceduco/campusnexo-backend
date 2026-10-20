package edu.campusnexo.application.event;

import edu.campusnexo.domain.event.TelemetryRecorded;

public interface TelemetryObserver {
    void onTelemetryRecorded(TelemetryRecorded event);
}
