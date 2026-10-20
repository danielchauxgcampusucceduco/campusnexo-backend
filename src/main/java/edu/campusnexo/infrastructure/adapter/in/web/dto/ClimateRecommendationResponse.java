package edu.campusnexo.infrastructure.web.dto;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.VentilationMode;
import edu.campusnexo.domain.occupancy.OccupancySource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClimateRecommendationResponse(
        UUID recommendationId,
        String spaceId,
        ClimateProfile climateProfile,
        Instant generatedAt,
        Hvac hvac,
        Ventilation ventilation,
        Lighting lighting,
        List<String> explanations,
        OccupancySource occupancySource,
        Instant sourceTelemetryAt,
        Inputs inputs) {

    public static ClimateRecommendationResponse from(ClimateRecommendation recommendation) {
        return new ClimateRecommendationResponse(recommendation.recommendationId(), recommendation.spaceId(),
                recommendation.climateProfile(), recommendation.generatedAt(),
                new Hvac(recommendation.hvac().mode(), recommendation.hvac().enabled(),
                        recommendation.hvac().setpointC(), recommendation.hvac().explanation()),
                new Ventilation(recommendation.ventilation().mode(), recommendation.ventilation().enabled(),
                        recommendation.ventilation().explanation()),
                new Lighting(recommendation.lighting().targetLevelPct(), recommendation.lighting().closeBlinds(),
                        recommendation.lighting().explanation()),
                recommendation.explanations(), recommendation.occupancySource(), recommendation.sourceTelemetryAt(),
                Inputs.from(recommendation.inputs()));
    }

    public record Hvac(HvacMode mode, boolean enabled, double setpointC, String explanation) {
    }

    public record Ventilation(VentilationMode mode, boolean enabled, String explanation) {
    }

    public record Lighting(int targetLevelPct, boolean closeBlinds, String explanation) {
    }

    public record Inputs(int occupancy, double co2Ppm, double indoorTemperatureC, double outsideTemperatureC,
                         double humidityPct, double illuminanceLux, HvacMode currentHvacMode,
                         int minutesInCurrentHvacMode) {
        static Inputs from(ClimateContext context) {
            if (context == null) {
                return null;
            }
            return new Inputs(context.occupancy(), context.co2Ppm(), context.indoorTemperatureC(),
                    context.outsideTemperatureC(), context.humidityPct(), context.illuminanceLux(),
                    context.currentHvac().mode(), context.currentHvac().minutesInMode());
        }
    }
}
