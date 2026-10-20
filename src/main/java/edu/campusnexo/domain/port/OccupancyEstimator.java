package edu.campusnexo.domain.port;

import edu.campusnexo.domain.model.OccupancyEstimate;

public interface OccupancyEstimator {
    OccupancyEstimate estimate(String spaceId, Integer yoloPeople, double co2Ppm,
                               int wifiDevices, double outdoorCo2Ppm);
}
