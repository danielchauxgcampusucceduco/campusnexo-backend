package edu.campusnexo.domain.port;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyQuery;

/**
 * Target interface of the Adapter pattern and component interface of the Decorator pattern.
 * Implementations may throw {@code OccupancyEstimationFailedException}.
 */
public interface OccupancyEstimator {
    OccupancyEstimate estimate(OccupancyQuery query);
}
