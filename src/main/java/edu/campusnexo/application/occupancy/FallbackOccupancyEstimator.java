package edu.campusnexo.application.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimationFailedException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.port.OccupancyEstimator;

/**
 * Decorator: when the wrapped estimator fails, answers with a secondary estimator so the campus
 * keeps working while the AI service is down. The result is marked with its real source.
 */
public class FallbackOccupancyEstimator implements OccupancyEstimator {
    private final OccupancyEstimator primary;
    private final OccupancyEstimator fallback;

    public FallbackOccupancyEstimator(OccupancyEstimator primary, OccupancyEstimator fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        try {
            return primary.estimate(query);
        } catch (OccupancyEstimationFailedException failure) {
            return fallback.estimate(query);
        }
    }
}
