package edu.campusnexo.infrastructure.persistence;

import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.model.ClimateProfile;
import edu.campusnexo.domain.policy.HvacDecision;
import edu.campusnexo.domain.policy.LightingDecision;
import edu.campusnexo.domain.policy.VentilationDecision;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "climate_recommendations", indexes =
        @Index(name = "idx_recommendations_space_time", columnList = "space_id, generated_at"))
class ClimateRecommendationEntity {
    @Id @Column(name = "recommendation_id", nullable = false) private UUID recommendationId;
    @Column(name = "space_id", nullable = false, length = 32) private String spaceId;
    @Enumerated(EnumType.STRING) @Column(name = "climate_profile", nullable = false, length = 24) private ClimateProfile climateProfile;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "hvac_enabled", nullable = false) private boolean hvacEnabled;
    @Column(name = "hvac_setpoint_c", nullable = false) private int hvacSetpointC;
    @Column(name = "hvac_explanation", nullable = false, columnDefinition = "TEXT") private String hvacExplanation;
    @Column(name = "ventilation_enabled", nullable = false) private boolean ventilationEnabled;
    @Column(name = "ventilation_explanation", nullable = false, columnDefinition = "TEXT") private String ventilationExplanation;
    @Column(name = "lighting_level_pct", nullable = false) private int lightingLevelPct;
    @Column(name = "lighting_explanation", nullable = false, columnDefinition = "TEXT") private String lightingExplanation;
    @Column(name = "explanation_text", nullable = false, columnDefinition = "TEXT") private String explanationText;
    protected ClimateRecommendationEntity() {}
    ClimateRecommendationEntity(ClimateRecommendation recommendation) {
        recommendationId = recommendation.recommendationId();
        spaceId = recommendation.spaceId();
        climateProfile = recommendation.climateProfile();
        generatedAt = recommendation.generatedAt();
        hvacEnabled = recommendation.hvac().enabled();
        hvacSetpointC = recommendation.hvac().setpointC();
        hvacExplanation = recommendation.hvac().explanation();
        ventilationEnabled = recommendation.ventilation().enabled();
        ventilationExplanation = recommendation.ventilation().explanation();
        lightingLevelPct = recommendation.lighting().targetLevelPct();
        lightingExplanation = recommendation.lighting().explanation();
        explanationText = String.join("; ", recommendation.explanations());
    }

    ClimateRecommendation toDomain() {
        return new ClimateRecommendation(recommendationId, spaceId, climateProfile, generatedAt,
                new HvacDecision(hvacEnabled, hvacSetpointC, hvacExplanation),
                new VentilationDecision(ventilationEnabled, ventilationExplanation),
                new LightingDecision(lightingLevelPct, lightingExplanation),
                List.of(hvacExplanation, ventilationExplanation, lightingExplanation));
    }
}
