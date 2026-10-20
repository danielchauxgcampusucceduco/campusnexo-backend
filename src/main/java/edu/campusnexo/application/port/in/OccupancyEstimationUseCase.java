package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.model.OccupancyEstimate;

public interface OccupancyEstimationUseCase {
    OccupancyEstimate estimate(String spaceId, Integer yoloPeople, double co2Ppm,
                               int wifiDevices, double outdoorCo2Ppm);
}
