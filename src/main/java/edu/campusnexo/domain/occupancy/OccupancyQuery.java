package edu.campusnexo.domain.occupancy;

import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

/** Signals sent to an occupancy estimator: the camera count and the Wi-Fi devices, both optional. */
public record OccupancyQuery(
        String spaceId,
        int capacity,
        double areaSqm,
        Integer cameraPeople,
        Integer wifiDevices) {

    public OccupancyQuery {
        requireText(spaceId, "spaceId");
        requireRange(capacity, 1, MAX_PEOPLE, "capacity");
        requireRange(areaSqm, 0.1, 100_000, "areaSqm");
        if (cameraPeople != null) {
            requireRange(cameraPeople, 0, MAX_PEOPLE, "cameraPeople");
        }
        if (wifiDevices != null) {
            requireRange(wifiDevices, 0, MAX_DEVICES, "wifiDevices");
        }
    }
}
