package edu.campusnexo.domain.occupancy;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_PEOPLE;

/** Value returned by an occupancy estimator. */
public record OccupancyEstimate(
        String spaceId,
        int occupancyEstimate,
        double confidencePct,
        boolean cameraAvailable,
        boolean isEstimate,
        OccupancySource source,
        String note) {

    public OccupancyEstimate {
        requireText(spaceId, "spaceId");
        requireRange(occupancyEstimate, 0, MAX_PEOPLE, "occupancyEstimate");
        requireRange(confidencePct, 0, 100, "confidencePct");
        requireNonNull(source, "source");
        note = note == null ? "" : note;
    }
}
