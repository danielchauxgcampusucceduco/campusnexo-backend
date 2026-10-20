package edu.campusnexo.application.climate;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.model.ClimateProfile;
import edu.campusnexo.domain.policy.ClimateContext;
import edu.campusnexo.domain.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ClimateRecommendationService {
    private final Clock clock;
    private final SpaceCatalogService spaces;
    private final ClimateRecommendationRepository recommendations;
    private final Map<ClimateProfile, ClimatePolicyFactory> factories;
    public ClimateRecommendationService(Clock clock, SpaceCatalogService spaces,
            ClimateRecommendationRepository recommendations, List<ClimatePolicyFactory> factories) {
        this.clock = clock;
        this.spaces = spaces;
        this.recommendations = recommendations;
        EnumMap<ClimateProfile, ClimatePolicyFactory> byProfile = new EnumMap<>(ClimateProfile.class);
        for (ClimatePolicyFactory factory : factories) {
            if (byProfile.put(factory.climateProfile(), factory) != null) {
                throw new IllegalStateException("Hay más de una fábrica para " + factory.climateProfile());
            }
        }
        this.factories = Map.copyOf(byProfile);
    }
    public ClimateRecommendation recommend(String spaceId, ClimateContext context) {
        var space = spaces.getRequired(spaceId);
        ClimatePolicyFactory factory = factories.get(space.climateProfile());
        if (factory == null) throw new IllegalStateException("No hay políticas para " + space.climateProfile());
        var hvac = factory.createThermalPolicy().recommend(context);
        var ventilation = factory.createVentilationPolicy().recommend(context);
        var lighting = factory.createLightingPolicy().recommend(context);
        ClimateRecommendation recommendation = ClimateRecommendation.builder()
                .spaceId(space.spaceId()).climateProfile(space.climateProfile()).generatedAt(clock.instant())
                .hvac(hvac).ventilation(ventilation).lighting(lighting).build();
        return recommendations.save(recommendation);
    }
}
