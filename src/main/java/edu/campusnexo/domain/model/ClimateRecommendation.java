package edu.campusnexo.domain.model;

import edu.campusnexo.domain.policy.HvacDecision;
import edu.campusnexo.domain.policy.LightingDecision;
import edu.campusnexo.domain.policy.VentilationDecision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable recommendation. Its Builder prevents partially populated decisions. */
public record ClimateRecommendation(
        UUID recommendationId,
        String spaceId,
        ClimateProfile climateProfile,
        Instant generatedAt,
        HvacDecision hvac,
        VentilationDecision ventilation,
        LightingDecision lighting,
        List<String> explanations) {
    public ClimateRecommendation {
        if (recommendationId == null) recommendationId = UUID.randomUUID();
        if (spaceId == null || spaceId.isBlank() || climateProfile == null || generatedAt == null
                || hvac == null || ventilation == null || lighting == null) {
            throw new IllegalArgumentException("Una recomendación requiere espacio, perfil, instante y decisiones completas");
        }
        explanations = List.of(hvac.explanation(), ventilation.explanation(), lighting.explanation());
    }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID recommendationId;
        private String spaceId;
        private ClimateProfile climateProfile;
        private Instant generatedAt;
        private HvacDecision hvac;
        private VentilationDecision ventilation;
        private LightingDecision lighting;
        private Builder() {}
        public Builder recommendationId(UUID value) { recommendationId = value; return this; }
        public Builder spaceId(String value) { spaceId = value; return this; }
        public Builder climateProfile(ClimateProfile value) { climateProfile = value; return this; }
        public Builder generatedAt(Instant value) { generatedAt = value; return this; }
        public Builder hvac(HvacDecision value) { hvac = value; return this; }
        public Builder ventilation(VentilationDecision value) { ventilation = value; return this; }
        public Builder lighting(LightingDecision value) { lighting = value; return this; }
        public ClimateRecommendation build() {
            if (spaceId == null || spaceId.isBlank()) throw new IllegalStateException("spaceId es obligatorio");
            if (climateProfile == null || generatedAt == null || hvac == null || ventilation == null || lighting == null) {
                throw new IllegalStateException("Perfil, instante y decisiones HVAC, ventilación e iluminación son obligatorios");
            }
            return new ClimateRecommendation(recommendationId, spaceId, climateProfile, generatedAt, hvac, ventilation, lighting, List.of());
        }
    }
}
