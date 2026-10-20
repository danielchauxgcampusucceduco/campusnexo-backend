package edu.campusnexo.domain.occupancy;

import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

/** Aggregated signals sent to an occupancy estimator. {@code cameraPeople} is optional. */
public record OccupancyQuery(
        String spaceId,
        int capacity,
        double areaSqm,
        Integer cameraPeople,
        double co2Ppm,
        int wifiDevices,
        double outdoorCo2Ppm) {

    public OccupancyQuery {
        requireText(spaceId, "spaceId");
        requireRange(capacity, 1, MAX_PEOPLE, "capacity");
        requireRange(areaSqm, 0.1, 100_000, "areaSqm");
        if (cameraPeople != null) {
            requireRange(cameraPeople, 0, MAX_PEOPLE, "cameraPeople");
        }
        requireRange(co2Ppm, MIN_CO2_PPM, MAX_CO2_PPM, "co2Ppm");
        requireRange(wifiDevices, 0, MAX_DEVICES, "wifiDevices");
        requireRange(outdoorCo2Ppm, MIN_OUTDOOR_CO2_PPM, MAX_OUTDOOR_CO2_PPM, "outdoorCo2Ppm");
    }
}
