package edu.campusnexo.infrastructure.adapter.in.web.dto;

import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_HUMIDITY_PCT;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_ILLUMINANCE_LUX;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_PEOPLE;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_TEMPERATURE_C;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_CO2_PPM;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_HUMIDITY_PCT;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_ILLUMINANCE_LUX;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_TEMPERATURE_C;

import edu.campusnexo.application.port.in.RecommendClimateCommand;
import edu.campusnexo.domain.climate.HvacMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Manual recommendation request. {@code outsideTemperatureC} is optional (branch average);
 * {@code currentHvacMode} and {@code minutesInCurrentHvacMode} go together or are omitted so the
 * server derives them from the recommendation history.
 */
public record ClimateRecommendationRequest(
        @NotNull @Min(0) @Max(MAX_PEOPLE) Integer occupancy,
        @NotNull @DecimalMin("" + MIN_CO2_PPM) @DecimalMax("" + MAX_CO2_PPM) Double co2Ppm,
        @NotNull @DecimalMin("" + MIN_TEMPERATURE_C) @DecimalMax("" + MAX_TEMPERATURE_C) Double indoorTemperatureC,
        @DecimalMin("" + MIN_TEMPERATURE_C) @DecimalMax("" + MAX_TEMPERATURE_C) Double outsideTemperatureC,
        @NotNull @DecimalMin("" + MIN_HUMIDITY_PCT) @DecimalMax("" + MAX_HUMIDITY_PCT) Double humidityPct,
        @NotNull @DecimalMin("" + MIN_ILLUMINANCE_LUX) @DecimalMax("" + MAX_ILLUMINANCE_LUX) Double illuminanceLux,
        HvacMode currentHvacMode,
        @Min(0) Integer minutesInCurrentHvacMode) {

    public RecommendClimateCommand toCommand(String spaceId) {
        return new RecommendClimateCommand(spaceId, occupancy, co2Ppm, indoorTemperatureC, outsideTemperatureC,
                humidityPct, illuminanceLux, currentHvacMode, minutesInCurrentHvacMode, null, null);
    }
}
