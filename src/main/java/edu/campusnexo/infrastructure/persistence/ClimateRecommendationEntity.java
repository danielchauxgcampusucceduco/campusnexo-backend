package edu.campusnexo.infrastructure.persistence;

import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.model.ClimateProfile;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "climate_recommendations", indexes = @Index(name = "idx_recommendations_space_time", columnList = "space_id, generated_at"))
class ClimateRecommendationEntity {
    @Id @Column(name = "recommendation_id", nullable = false) private UUID recommendationId;
    @Column(name = "space_id", nullable = false, length = 32) private String spaceId;
    @Enumerated(EnumType.STRING) @Column(name = "climate_profile", nullable = false, length = 24) private ClimateProfile climateProfile;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "hvac_enabled", nullable = false) private boolean hvacEnabled;
    @Column(name = "hvac_setpoint_c", nullable = false) private int hvacSetpointC;
    @Column(name = "ventilation_enabled", nullable = false) private boolean ventilationEnabled;
    @Column(name = "lighting_level_pct", nullable = false) private int lightingLevelPct;
    @Column(name = "explanation_text", nullable = false, columnDefinition = "TEXT") private String explanationText;
    protected ClimateRecommendationEntity() {}
    ClimateRecommendationEntity(ClimateRecommendation recommendation) {
        recommendationId = recommendation.recommendationId();
        spaceId = recommendation.spaceId();
        climateProfile = recommendation.climateProfile();
        generatedAt = recommendation.generatedAt();
        hvacEnabled = recommendation.hvac().enabled();
        hvacSetpointC = recommendation.hvac().setpointC();
        ventilationEnabled = recommendation.ventilation().enabled();
        lightingLevelPct = recommendation.lighting().targetLevelPct();
        explanationText = String.join("; ", recommendation.explanations());
    }
}
