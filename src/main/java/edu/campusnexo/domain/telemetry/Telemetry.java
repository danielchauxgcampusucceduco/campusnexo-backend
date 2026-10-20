package edu.campusnexo.domain.telemetry;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

import java.time.Instant;

/**
 * Aggregated reading of a space. {@code peopleCount} is the aggregated count reported by the
 * sensor or camera pipeline; {@code objectCount} counts objects left on desks (used by the AI
 * service to detect seats held without people). No image or personal identifier is stored.
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
        double activePowerKw) {

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
    }

    public boolean isEmpty() {
        return peopleCount == 0;
    }
}
