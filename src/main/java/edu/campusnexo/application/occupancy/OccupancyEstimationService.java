package edu.campusnexo.application.occupancy;

import edu.campusnexo.application.exception.ResourceNotFoundException;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.EstimateOccupancyCommand;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.occupancy.OccupancyRecord;
import edu.campusnexo.domain.port.OccupancyEstimateRepository;
import edu.campusnexo.domain.port.OccupancyEstimator;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class OccupancyEstimationService implements OccupancyEstimationUseCase {
    private final Clock clock;
    private final OccupancyEstimator estimator;
    private final OccupancyEstimateRepository repository;
    private final CampusCatalogUseCase catalog;
    private final double defaultOutdoorCo2Ppm;

    public OccupancyEstimationService(Clock clock, OccupancyEstimator estimator, OccupancyEstimateRepository repository,
                                      CampusCatalogUseCase catalog, double defaultOutdoorCo2Ppm) {
        this.clock = clock;
        this.estimator = estimator;
        this.repository = repository;
        this.catalog = catalog;
        this.defaultOutdoorCo2Ppm = defaultOutdoorCo2Ppm;
    }

    @Override
    public OccupancyRecord estimate(EstimateOccupancyCommand command) {
        CampusSpace space = catalog.getRequiredSpace(command.spaceId());
        double outdoor = command.outdoorCo2Ppm() == null ? defaultOutdoorCo2Ppm : command.outdoorCo2Ppm();
        OccupancyQuery query = new OccupancyQuery(space.spaceId(), space.capacity(), space.areaSqm(),
                command.cameraPeople(), command.co2Ppm(), command.wifiDevices(), outdoor);
        OccupancyEstimate estimate = estimator.estimate(query);
        return repository.save(new OccupancyRecord(UUID.randomUUID(), clock.instant(), estimate));
    }

    @Override
    public OccupancyRecord get(UUID estimateId) {
        return repository.findById(estimateId).orElseThrow(() -> new ResourceNotFoundException(
                "occupancy_estimate_not_found", "No existe la estimación: " + estimateId));
    }

    @Override
    public List<OccupancyRecord> recent(String spaceId, int limit) {
        ResultLimit resultLimit = ResultLimit.of(limit);
        catalog.getRequiredSpace(spaceId);
        return repository.findRecent(spaceId, resultLimit);
    }
}
