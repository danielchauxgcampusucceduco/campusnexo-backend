package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

import java.time.Instant;

/**
 * Reading of the room's Arduino: temperature and humidity (DHT22), light level (light sensor), lighting
 * relay state and computers drawing power (current sensor). {@code co2Ppm} is optional, for rooms with a
 * CO2 sensor; when it is {@code null} the last known value is kept.
 */
public record EnvironmentReading(String spaceId, Instant capturedAt, double temperatureC, double humidityPct,
                                 Double co2Ppm, double illuminanceLux, boolean lightsOn, int devicesOn) {
    public EnvironmentReading {
        requireText(spaceId, "spaceId");
        requireNonNull(capturedAt, "capturedAt");
        requireRange(temperatureC, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C, "temperatureC");
        requireRange(humidityPct, MIN_HUMIDITY_PCT, MAX_HUMIDITY_PCT, "humidityPct");
        if (co2Ppm != null) {
            requireRange(co2Ppm, MIN_CO2_PPM, MAX_CO2_PPM, "co2Ppm");
        }
        requireRange(illuminanceLux, MIN_ILLUMINANCE_LUX, MAX_ILLUMINANCE_LUX, "illuminanceLux");
        requireRange(devicesOn, 0, MAX_DEVICES, "devicesOn");
    }
}
