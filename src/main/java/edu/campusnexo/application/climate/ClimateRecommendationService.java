package edu.campusnexo.application.climate;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ClimateRecommendationUseCase;
import edu.campusnexo.application.port.in.RecommendClimateCommand;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.catalog.ClimateProfile;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.climate.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client of the Abstract Factory: picks the policy family of the branch climate profile and runs
 * its three strategies, then assembles the recommendation with the Builder.
 */
public class ClimateRecommendationService implements ClimateRecommendationUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final ClimateRecommendationRepository repository;
    private final HvacStateResolver hvacStateResolver;
    private final Map<ClimateProfile, ClimatePolicyFactory> factories;

    public ClimateRecommendationService(Clock clock, CampusCatalogUseCase catalog,
                                        ClimateRecommendationRepository repository,
                                        HvacStateResolver hvacStateResolver,
                                        List<ClimatePolicyFactory> factories) {
        this.clock = clock;
        this.catalog = catalog;
        this.repository = repository;
        this.hvacStateResolver = hvacStateResolver;
        EnumMap<ClimateProfile, ClimatePolicyFactory> byProfile = new EnumMap<>(ClimateProfile.class);
        for (ClimatePolicyFactory factory : factories) {
            if (byProfile.put(factory.climateProfile(), factory) != null) {
                throw new IllegalStateException("Hay más de una fábrica para " + factory.climateProfile());
            }
        }
        for (ClimateProfile profile : ClimateProfile.values()) {
            if (!byProfile.containsKey(profile)) {
                throw new IllegalStateException("Falta la fábrica de políticas para " + profile);
            }
        }
        this.factories = Map.copyOf(byProfile);
    }

    @Override
    public ClimateRecommendation recommend(RecommendClimateCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        ClimateContext context = new ClimateContext(command.occupancy(), command.co2Ppm(),
                command.indoorTemperatureC(),
                command.outsideTemperatureC() == null ? space.averageOutsideTempC() : command.outsideTemperatureC(),
                command.humidityPct(), command.illuminanceLux(), currentHvacState(command));

        ClimatePolicyFactory family = factories.get(space.climateProfile());
        ClimateRecommendation recommendation = ClimateRecommendation.builder()
                .spaceId(space.spaceId())
                .climateProfile(space.climateProfile())
                .generatedAt(clock.instant())
                .hvac(family.createThermalPolicy().recommend(context))
                .ventilation(family.createVentilationPolicy().recommend(context))
                .lighting(family.createLightingPolicy().recommend(context))
                .occupancySource(command.occupancySource() == null ? OccupancySource.MANUAL : command.occupancySource())
                .sourceTelemetryAt(command.sourceTelemetryAt())
                .inputs(context)
                .build();
        return repository.save(recommendation);
    }

    @Override
    public ClimateRecommendation get(UUID recommendationId) {
        return repository.findById(recommendationId).orElseThrow(() -> new ResourceNotFoundException(
                "recommendation_not_found", "No existe la recomendación: " + recommendationId));
    }

    @Override
    public List<ClimateRecommendation> recent(String spaceId, int limit) {
        ResultLimit resultLimit = ResultLimit.of(limit);
        String filter = spaceId == null || spaceId.isBlank() ? null : spaceId;
        if (filter != null) {
            catalog.getRequiredSpace(filter);
        }
        return repository.findRecent(filter, resultLimit);
    }

    private HvacState currentHvacState(RecommendClimateCommand command) {
        if (command.currentHvacMode() == null && command.minutesInCurrentHvacMode() == null) {
            return hvacStateResolver.resolve(command.spaceId());
        }
        if (command.currentHvacMode() == null || command.minutesInCurrentHvacMode() == null) {
            throw new DomainValidationException(
                    "currentHvacMode y minutesInCurrentHvacMode se envían juntos o se omiten juntos");
        }
        return new HvacState(command.currentHvacMode(), command.minutesInCurrentHvacMode());
    }
}
