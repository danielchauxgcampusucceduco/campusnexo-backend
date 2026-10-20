package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.occupancy.OccupancySource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable, auditable recommendation. Validation lives only in this canonical constructor.
 *
 * <p>Builder pattern: the use case assembles the recommendation step by step (decisions coming
 * from three different strategies plus optional provenance data: occupancy source, source
 * telemetry instant and the input snapshot). {@link Builder} supplies defaults for the optional
 * parts and generates the identifier, so callers never deal with a ten-argument constructor.
 *
 * <p>{@code inputs} and {@code sourceTelemetryAt} may be {@code null} for rows created before
 * those fields were audited, or when the request did not come from stored telemetry.
 */
public record ClimateRecommendation(
        UUID recommendationId,
        String spaceId,
        ClimateProfile climateProfile,
        Instant generatedAt,
        HvacDecision hvac,
        VentilationDecision ventilation,
        LightingDecision lighting,
        OccupancySource occupancySource,
        Instant sourceTelemetryAt,
        ClimateContext inputs) {

    public ClimateRecommendation {
        requireNonNull(recommendationId, "recommendationId");
        requireText(spaceId, "spaceId");
        requireNonNull(climateProfile, "climateProfile");
        requireNonNull(generatedAt, "generatedAt");
        requireNonNull(hvac, "hvac");
        requireNonNull(ventilation, "ventilation");
        requireNonNull(lighting, "lighting");
        requireNonNull(occupancySource, "occupancySource");
    }

    public List<String> explanations() {
        return List.of(hvac.explanation(), ventilation.explanation(), lighting.explanation());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID recommendationId;
        private String spaceId;
        private ClimateProfile climateProfile;
        private Instant generatedAt;
        private HvacDecision hvac;
        private VentilationDecision ventilation;
        private LightingDecision lighting;
        private OccupancySource occupancySource = OccupancySource.MANUAL;
        private Instant sourceTelemetryAt;
        private ClimateContext inputs;

        private Builder() {
        }

        public Builder recommendationId(UUID value) {
            recommendationId = value;
            return this;
        }

        public Builder spaceId(String value) {
            spaceId = value;
            return this;
        }

        public Builder climateProfile(ClimateProfile value) {
            climateProfile = value;
            return this;
        }

        public Builder generatedAt(Instant value) {
            generatedAt = value;
            return this;
        }

        public Builder hvac(HvacDecision value) {
            hvac = value;
            return this;
        }

        public Builder ventilation(VentilationDecision value) {
            ventilation = value;
            return this;
        }

        public Builder lighting(LightingDecision value) {
            lighting = value;
            return this;
        }

        public Builder occupancySource(OccupancySource value) {
            occupancySource = value;
            return this;
        }

        public Builder sourceTelemetryAt(Instant value) {
            sourceTelemetryAt = value;
            return this;
        }

        public Builder inputs(ClimateContext value) {
            inputs = value;
            return this;
        }

        public ClimateRecommendation build() {
            UUID id = recommendationId == null ? UUID.randomUUID() : recommendationId;
            return new ClimateRecommendation(id, spaceId, climateProfile, generatedAt, hvac, ventilation,
                    lighting, occupancySource, sourceTelemetryAt, inputs);
        }
    }
}
