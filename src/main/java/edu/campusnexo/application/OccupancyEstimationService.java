package edu.campusnexo.application;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.domain.model.OccupancyEstimate;
import edu.campusnexo.domain.port.OccupancyEstimator;

public class OccupancyEstimationService implements OccupancyEstimationUseCase {
    private final OccupancyEstimator estimator;
    private final CampusCatalogUseCase spaces;
    public OccupancyEstimationService(OccupancyEstimator estimator, CampusCatalogUseCase spaces) {
        this.estimator = estimator;
        this.spaces = spaces;
    }
    @Override public OccupancyEstimate estimate(String spaceId, Integer yoloPeople, double co2Ppm,
                                      int wifiDevices, double outdoorCo2Ppm) {
        spaces.getRequiredSpace(spaceId);
        return estimator.estimate(spaceId, yoloPeople, co2Ppm, wifiDevices, outdoorCo2Ppm);
    }
}
