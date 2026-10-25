package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.occupancy.OccupancySource;
import java.time.Instant;

/**
 * Inputs of a climate recommendation. Optional values: {@code outsideTemperatureC} (branch
 * average), {@code currentHvacMode} and {@code minutesInCurrentHvacMode} (derived from the
 * recommendation history when both are missing), {@code occupancySource} (MANUAL) and
 * {@code sourceTelemetryAt}.
 */
public record RecommendClimateCommand(
        String spaceId,
        int occupancy,
        double co2Ppm,
        double indoorTemperatureC,
        Double outsideTemperatureC,
        double humidityPct,
        double illuminanceLux,
        HvacMode currentHvacMode,
        Integer minutesInCurrentHvacMode,
        OccupancySource occupancySource,
        Instant sourceTelemetryAt) {
}
