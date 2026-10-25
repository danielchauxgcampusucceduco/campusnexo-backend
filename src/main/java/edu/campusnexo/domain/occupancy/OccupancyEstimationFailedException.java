package edu.campusnexo.domain.occupancy;

/** Base failure of an {@code OccupancyEstimator}; decorators can recover from it. */
public abstract class OccupancyEstimationFailedException extends RuntimeException {
    protected OccupancyEstimationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
