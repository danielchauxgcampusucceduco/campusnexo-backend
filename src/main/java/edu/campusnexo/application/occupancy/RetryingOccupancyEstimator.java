package edu.campusnexo.application.occupancy;

import edu.campusnexo.domain.occupancy.OccupancyEstimate;
import edu.campusnexo.domain.occupancy.OccupancyEstimatorUnavailableException;
import edu.campusnexo.domain.occupancy.OccupancyQuery;
import edu.campusnexo.domain.port.OccupancyEstimator;

/**
 * Decorator: retries the wrapped estimator when it is unavailable (timeouts, connection errors).
 * Invalid responses are not retried because repeating the call would return the same data.
 */
public class RetryingOccupancyEstimator implements OccupancyEstimator {
    private final OccupancyEstimator delegate;
    private final int maxAttempts;

    public RetryingOccupancyEstimator(OccupancyEstimator delegate, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public OccupancyEstimate estimate(OccupancyQuery query) {
        OccupancyEstimatorUnavailableException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.estimate(query);
            } catch (OccupancyEstimatorUnavailableException failure) {
                last = failure;
            }
        }
        throw last;
    }
}
