package edu.campusnexo.infrastructure.adapter.in.web.dto;

import edu.campusnexo.application.port.in.RecommendClimateCommand;
import edu.campusnexo.domain.climate.HvacMode;
import jakarta.validation.constraints.NotNull;

/**
 * Manual recommendation request. {@code outsideTemperatureC} is optional (branch average);
 * {@code currentHvacMode} and {@code minutesInCurrentHvacMode} go together or are omitted so the
 * server derives them from the recommendation history.
 */
public record ClimateRecommendationRequest(
        @NotNull Integer occupancy,
        @NotNull Double co2Ppm,
        @NotNull Double indoorTemperatureC,
        Double outsideTemperatureC,
        @NotNull Double humidityPct,
        @NotNull Double illuminanceLux,
        HvacMode currentHvacMode,
        Integer minutesInCurrentHvacMode) {

    public RecommendClimateCommand toCommand(String spaceId) {
        return new RecommendClimateCommand(spaceId, occupancy, co2Ppm, indoorTemperatureC, outsideTemperatureC,
                humidityPct, illuminanceLux, currentHvacMode, minutesInCurrentHvacMode, null, null);
    }
}
