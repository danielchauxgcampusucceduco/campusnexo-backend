package edu.campusnexo.application;

import edu.campusnexo.application.catalog.SpaceCatalogService;
import edu.campusnexo.domain.model.OccupancyEstimate;
import edu.campusnexo.domain.port.OccupancyEstimator;

public class OccupancyEstimationService {
    private final OccupancyEstimator estimator;
    private final SpaceCatalogService spaces;
    public OccupancyEstimationService(OccupancyEstimator estimator, SpaceCatalogService spaces) {
        this.estimator = estimator;
        this.spaces = spaces;
    }
    public OccupancyEstimate estimate(String spaceId, Integer yoloPeople, double co2Ppm,
                                      int wifiDevices, double outdoorCo2Ppm) {
        spaces.getRequired(spaceId);
        return estimator.estimate(spaceId, yoloPeople, co2Ppm, wifiDevices, outdoorCo2Ppm);
    }
}
