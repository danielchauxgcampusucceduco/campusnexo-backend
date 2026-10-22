package edu.campusnexo.domain.telemetry;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

import java.time.Instant;

/**
 * Aggregated reading of a space. {@code peopleCount} is the occupancy recorded for the room (for camera
 * frames, the Kalman estimate that fuses camera, CO₂ and Wi-Fi); {@code cameraPeople} is the camera's own
 * count in that frame and is {@code null} when no camera saw the room. Keeping both apart lets the AI
 * service fuse raw measurements instead of an estimate that already contains them. {@code objectCount}
 * counts objects left on desks. No image or personal identifier is stored.
 */
public record Telemetry(
        String spaceId,
        Instant recordedAt,
        int peopleCount,
        int objectCount,
        int wifiDevices,
        double co2Ppm,
        double temperatureC,
        double humidityPct,
        double illuminanceLux,
        double activePowerKw,
        Integer cameraPeople) {

    public Telemetry {
        requireText(spaceId, "spaceId");
        requireNonNull(recordedAt, "recordedAt");
        requireRange(peopleCount, 0, MAX_PEOPLE, "peopleCount");
        requireRange(objectCount, 0, MAX_DEVICES, "objectCount");
        requireRange(wifiDevices, 0, MAX_DEVICES, "wifiDevices");
        requireRange(co2Ppm, MIN_CO2_PPM, MAX_CO2_PPM, "co2Ppm");
        requireRange(temperatureC, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C, "temperatureC");
        requireRange(humidityPct, MIN_HUMIDITY_PCT, MAX_HUMIDITY_PCT, "humidityPct");
        requireRange(illuminanceLux, MIN_ILLUMINANCE_LUX, MAX_ILLUMINANCE_LUX, "illuminanceLux");
        requireRange(activePowerKw, MIN_POWER_KW, MAX_POWER_KW, "activePowerKw");
        if (cameraPeople != null) {
            requireRange(cameraPeople, 0, MAX_PEOPLE, "cameraPeople");
        }
    }

    /** Reading from a sensor or tool that does not separate a camera count. */
    public Telemetry(String spaceId, Instant recordedAt, int peopleCount, int objectCount, int wifiDevices,
                     double co2Ppm, double temperatureC, double humidityPct, double illuminanceLux,
                     double activePowerKw) {
        this(spaceId, recordedAt, peopleCount, objectCount, wifiDevices, co2Ppm, temperatureC, humidityPct,
                illuminanceLux, activePowerKw, null);
    }

    public boolean isEmpty() {
        return peopleCount == 0;
    }
}
