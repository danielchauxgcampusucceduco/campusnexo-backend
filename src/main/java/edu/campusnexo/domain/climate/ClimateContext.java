package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

/**
 * Inputs of a climate decision. {@code illuminanceLux} is assumed to measure the daylight
 * contribution (daylight sensor or artificial circuit off), as daylight-harvesting controls do.
 */
public record ClimateContext(
        int occupancy,
        double co2Ppm,
        double indoorTemperatureC,
        double outsideTemperatureC,
        double humidityPct,
        double illuminanceLux,
        HvacState currentHvac) {

    public ClimateContext {
        requireRange(occupancy, 0, MAX_PEOPLE, "occupancy");
        requireRange(co2Ppm, MIN_CO2_PPM, MAX_CO2_PPM, "co2Ppm");
        requireRange(indoorTemperatureC, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C, "indoorTemperatureC");
        requireRange(outsideTemperatureC, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C, "outsideTemperatureC");
        requireRange(humidityPct, MIN_HUMIDITY_PCT, MAX_HUMIDITY_PCT, "humidityPct");
        requireRange(illuminanceLux, MIN_ILLUMINANCE_LUX, MAX_ILLUMINANCE_LUX, "illuminanceLux");
        requireNonNull(currentHvac, "currentHvac");
    }

    public boolean occupied() {
        return occupancy > 0;
    }
}
