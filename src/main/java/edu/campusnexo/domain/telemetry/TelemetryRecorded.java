package edu.campusnexo.domain.telemetry;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

import edu.campusnexo.domain.catalog.CampusSpace;

/** Event published after a reading has been persisted. */
public record TelemetryRecorded(Telemetry telemetry, CampusSpace space) {
    public TelemetryRecorded {
        requireNonNull(telemetry, "telemetry");
        requireNonNull(space, "space");
    }
}
