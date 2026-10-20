package edu.campusnexo.infrastructure.persistence.climate;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.climate.LightingDecision;
import edu.campusnexo.domain.climate.VentilationDecision;
import edu.campusnexo.domain.climate.VentilationMode;
import edu.campusnexo.domain.occupancy.OccupancySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "climate_recommendations")
class ClimateRecommendationEntity {
    @Id
    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "space_id", nullable = false, length = 32)
    private String spaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "climate_profile", nullable = false, length = 24)
    private ClimateProfile climateProfile;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "hvac_mode", nullable = false, length = 24)
    private HvacMode hvacMode;

    @Column(name = "hvac_enabled", nullable = false)
    private boolean hvacEnabled;

    @Column(name = "hvac_setpoint_c", nullable = false)
    private double hvacSetpointC;

    @Column(name = "hvac_explanation", nullable = false, columnDefinition = "TEXT")
    private String hvacExplanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "ventilation_mode", nullable = false, length = 24)
    private VentilationMode ventilationMode;

    @Column(name = "ventilation_enabled", nullable = false)
    private boolean ventilationEnabled;

    @Column(name = "ventilation_explanation", nullable = false, columnDefinition = "TEXT")
    private String ventilationExplanation;

    @Column(name = "lighting_level_pct", nullable = false)
    private int lightingLevelPct;

    @Column(name = "lighting_close_blinds", nullable = false)
    private boolean lightingCloseBlinds;

    @Column(name = "lighting_explanation", nullable = false, columnDefinition = "TEXT")
    private String lightingExplanation;

    /** Legacy column kept because V4 rows point to it; new rows store the same joined text. */
    @Column(name = "explanation_text", nullable = false, columnDefinition = "TEXT")
    private String explanationText;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_source", nullable = false, length = 24)
    private OccupancySource occupancySource;

    @Column(name = "source_telemetry_at")
    private Instant sourceTelemetryAt;

    @Column(name = "input_occupancy")
    private Integer inputOccupancy;

    @Column(name = "input_co2_ppm")
    private Double inputCo2Ppm;

    @Column(name = "input_indoor_temperature_c")
    private Double inputIndoorTemperatureC;

    @Column(name = "input_outside_temperature_c")
    private Double inputOutsideTemperatureC;

    @Column(name = "input_humidity_pct")
    private Double inputHumidityPct;

    @Column(name = "input_illuminance_lux")
    private Double inputIlluminanceLux;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_hvac_mode", length = 24)
    private HvacMode inputHvacMode;

    @Column(name = "input_minutes_in_hvac_mode")
    private Integer inputMinutesInHvacMode;

    protected ClimateRecommendationEntity() {
    }

    ClimateRecommendationEntity(ClimateRecommendation recommendation) {
        recommendationId = recommendation.recommendationId();
        spaceId = recommendation.spaceId();
        climateProfile = recommendation.climateProfile();
        generatedAt = recommendation.generatedAt();
        hvacMode = recommendation.hvac().mode();
        hvacEnabled = recommendation.hvac().enabled();
        hvacSetpointC = recommendation.hvac().setpointC();
        hvacExplanation = recommendation.hvac().explanation();
        ventilationMode = recommendation.ventilation().mode();
        ventilationEnabled = recommendation.ventilation().enabled();
        ventilationExplanation = recommendation.ventilation().explanation();
        lightingLevelPct = recommendation.lighting().targetLevelPct();
        lightingCloseBlinds = recommendation.lighting().closeBlinds();
        lightingExplanation = recommendation.lighting().explanation();
        explanationText = String.join("; ", recommendation.explanations());
        occupancySource = recommendation.occupancySource();
        sourceTelemetryAt = recommendation.sourceTelemetryAt();
        ClimateContext inputs = recommendation.inputs();
        if (inputs != null) {
            inputOccupancy = inputs.occupancy();
            inputCo2Ppm = inputs.co2Ppm();
            inputIndoorTemperatureC = inputs.indoorTemperatureC();
            inputOutsideTemperatureC = inputs.outsideTemperatureC();
            inputHumidityPct = inputs.humidityPct();
            inputIlluminanceLux = inputs.illuminanceLux();
            inputHvacMode = inputs.currentHvac().mode();
            inputMinutesInHvacMode = inputs.currentHvac().minutesInMode();
        }
    }

    ClimateRecommendation toDomain() {
        return new ClimateRecommendation(recommendationId, spaceId, climateProfile, generatedAt,
                new HvacDecision(hvacMode, hvacSetpointC, hvacExplanation),
                new VentilationDecision(ventilationMode, ventilationExplanation),
                new LightingDecision(lightingLevelPct, lightingCloseBlinds, lightingExplanation),
                occupancySource, sourceTelemetryAt, inputsOrNull());
    }

    private ClimateContext inputsOrNull() {
        if (inputOccupancy == null || inputCo2Ppm == null || inputIndoorTemperatureC == null
                || inputOutsideTemperatureC == null || inputHumidityPct == null || inputIlluminanceLux == null
                || inputHvacMode == null || inputMinutesInHvacMode == null) {
            return null;
        }
        return new ClimateContext(inputOccupancy, inputCo2Ppm, inputIndoorTemperatureC, inputOutsideTemperatureC,
                inputHumidityPct, inputIlluminanceLux, new HvacState(inputHvacMode, inputMinutesInHvacMode));
    }
}
