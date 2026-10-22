package edu.campusnexo.application.climate;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ClimateRecommendationUseCase;
import edu.campusnexo.application.port.in.RecommendClimateCommand;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.climate.policy.ClimateFamilies;
import edu.campusnexo.domain.climate.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.occupancy.OccupancySource;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.shared.DomainValidationException;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Client of the Abstract Factory: picks the policy family of the branch climate profile and runs
 * its three strategies, then assembles the recommendation with the Builder. The digital twin reads the same
 * family (comfort band, thermal strategy, compressor protection), so both answer with one notion of comfort.
 */
public class ClimateRecommendationService implements ClimateRecommendationUseCase {
    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final ClimateRecommendationRepository repository;
    private final HvacStateResolver hvacStateResolver;
    private final ClimateFamilies families;

    public ClimateRecommendationService(Clock clock, CampusCatalogUseCase catalog,
                                        ClimateRecommendationRepository repository,
                                        HvacStateResolver hvacStateResolver, ClimateFamilies families) {
        this.clock = clock;
        this.catalog = catalog;
        this.repository = repository;
        this.hvacStateResolver = hvacStateResolver;
        this.families = families;
    }

    public ClimateRecommendationService(Clock clock, CampusCatalogUseCase catalog,
                                        ClimateRecommendationRepository repository,
                                        HvacStateResolver hvacStateResolver, List<ClimatePolicyFactory> factories) {
        this(clock, catalog, repository, hvacStateResolver, new ClimateFamilies(factories));
    }

    @Override
    public ClimateRecommendation recommend(RecommendClimateCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        ClimateContext context = new ClimateContext(command.occupancy(), command.co2Ppm(),
                command.indoorTemperatureC(),
                command.outsideTemperatureC() == null ? space.averageOutsideTempC() : command.outsideTemperatureC(),
                command.humidityPct(), command.illuminanceLux(), currentHvacState(command));

        ClimatePolicyFactory family = families.of(space.climateProfile());
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
